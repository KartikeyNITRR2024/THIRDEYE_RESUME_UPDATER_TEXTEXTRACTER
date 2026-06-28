package com.thirdeye30.resumehelper.textextracter.services.impl;
import com.thirdeye30.resumehelper.textextracter.dtos.MailPayload;
import com.thirdeye30.resumehelper.textextracter.dtos.Message;
import com.thirdeye30.resumehelper.textextracter.dtos.PriorityDto;
import com.thirdeye30.resumehelper.textextracter.enums.MailType;
import com.thirdeye30.resumehelper.textextracter.enums.Status;
import com.thirdeye30.resumehelper.textextracter.services.MailService;
import com.thirdeye30.resumehelper.textextracter.services.MessageBrokerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {
    
    @Value("${thirdeye.mail.apikey}")
    private String brevoApiKey;
    
    private final MessageBrokerService messageBrokerService;
    
    @Qualifier("mailTaskExecutor")
    private final Executor mailTaskExecutor;
 
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void sendLinkInMail() {
        List<Message<MailPayload>> messagesToProcess = new ArrayList<>();

        while (true) {
            try {
                List<Message<MailPayload>> messages = messageBrokerService.getResumeMailPayloadMessage("mailprocesser");
                if (messages == null || messages.isEmpty()) {
                    break;
                }
                for (Message<MailPayload> message : messages) {
                    if (message != null && message.getMessage() != null) {
                    	if (message.getMessage().getEmail() == null || message.getMessage().getEmail().isEmpty())
                    	{
                    		continue;
                    	}
                        messagesToProcess.add(message);
                    }
                }
            } catch (Exception ex) {
                log.error("Error pulling messages from the mail queue", ex);
                break;
            }
        }

        if (messagesToProcess.isEmpty()) {
            return;
        }

        List<Message<MailPayload>> failedMessages = messagesToProcess;
        int maxTries = 3;
        List<PriorityDto> payloads = new ArrayList<>();

        for (int attempt = 1; attempt <= maxTries && !failedMessages.isEmpty(); attempt++) {
            if (attempt > 1) {
                log.info("Starting retry attempt #{} for {} failed emails via 5-thread pool...", attempt, failedMessages.size());
            } else {
                log.info("Processing {} emails concurrently using up to 5 worker threads...", failedMessages.size());
            }

            List<CompletableFuture<ActivationResult>> futures = failedMessages.stream()
                .map(msg -> CompletableFuture.supplyAsync(() -> {
                    boolean success = false;
                    if (msg.getMessage().getMailType() == MailType.SUBMIT) {
                        success = attemptEmailSend(msg.getMessage());
                    } else if (msg.getMessage().getMailType() == MailType.FETCHOLDHISTORY) {
                        success = attemptHistoryEmailSend(msg.getMessage());
                    } else if (msg.getMessage().getMailType() == MailType.COURSE){
                    	success = attemptCourseLinkEmailSend(msg.getMessage());
                    	if(success)
                    	{
                    		payloads.add(new PriorityDto(msg.getMessage().getId(), Status.MAILED_COMPLETED));
                    	}
                    }
                    return new ActivationResult(msg, success);
                }, mailTaskExecutor))
                .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            failedMessages = futures.stream()
                .map(CompletableFuture::join)
                .filter(result -> !result.isSuccess())
                .map(ActivationResult::getMessage)
                .collect(Collectors.toList());
        }
        
        if (!failedMessages.isEmpty()) {
        	for(Message<MailPayload> message : failedMessages)
        	{
        		MailPayload mailPayload = message.getMessage();
        		if (mailPayload.getMailType() == MailType.COURSE){
        			payloads.add(new PriorityDto(mailPayload.getId(), Status.MAILED_FAILED));
        		}
        	}
            log.error("{} emails completely failed to send after exhausting all {} async attempts.", failedMessages.size(), maxTries);
        }
        messageBrokerService.sendMessages("priorityskills", payloads);
    }

    private boolean attemptEmailSend(MailPayload payload) {
        try {
            StringBuilder htmlBody = new StringBuilder();
            htmlBody.append("<div style='font-family: Arial, sans-serif; padding: 20px; color: #333;'>");
            htmlBody.append("<h2>Your Updated Resumes are Ready!</h2>");
            htmlBody.append("<p>Hello ").append(payload.getName() != null ? payload.getName() : "User").append(",</p>");
            htmlBody.append("<p>We have successfully processed your resumes. You can find the links in the table below:</p>");

            htmlBody.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
            htmlBody.append("<tr style='background-color: #f2f2f2;'>");
            htmlBody.append("<th style='border: 1px solid #dddddd; text-align: left; padding: 8px;'>Resume Type</th>");
            htmlBody.append("<th style='border: 1px solid #dddddd; text-align: left; padding: 8px;'>Download Link</th>");
            htmlBody.append("</tr>");

            if (payload.getResumeUrls() != null && !payload.getResumeUrls().isEmpty()) {
                for (Map.Entry<String, String> entry : payload.getResumeUrls().entrySet()) {
                    htmlBody.append("<tr>");
                    htmlBody.append("<td style='border: 1px solid #dddddd; padding: 8px;'>").append(entry.getKey()).append("</td>");
                    htmlBody.append("<td style='border: 1px solid #dddddd; padding: 8px;'><a href='").append(entry.getValue()).append("' target='_blank'>Download</a></td>");
                    htmlBody.append("</tr>");
                }
            } else {
                htmlBody.append("<tr><td colspan='2' style='border: 1px solid #dddddd; padding: 8px; text-align: center;'>No resume links found.</td></tr>");
            }
            htmlBody.append("</table>");

            if (payload.getOldResumeUrl() != null && !payload.getOldResumeUrl().trim().isEmpty()) {
                htmlBody.append("<p style='margin-top: 15px;'>");
                htmlBody.append("<strong>Your Old Resumes:</strong> <a href='").append(payload.getOldResumeUrl()).append("' style='color: #0066cc;' target='_blank'>View old submission</a>");
                htmlBody.append("</p>");
            }

            if (payload.getJobUrl() != null && !payload.getJobUrl().trim().isEmpty()) {
                String formattedTime = payload.getCreationTime() != null 
                        ? payload.getCreationTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) 
                        : "recently";
                        
                htmlBody.append("<p style='font-size: 16px; font-weight: bold; margin-top: 20px;'>All the best for your job application!</p>");
                htmlBody.append("<p>Apply here: <a href='").append(payload.getJobUrl()).append("' style='color: #0066cc;' target='_blank'>Job Link</a></p>");
                htmlBody.append("<p style='font-size: 12px; color: #666;'>Processed at: ").append(formattedTime).append("</p>");
            } else {
                htmlBody.append("<p style='font-size: 16px; font-weight: bold; color: #2e7d32; margin-top: 20px;'>All the best for your career journey!</p>");
            }

            htmlBody.append("<br/><hr style='border: 0; border-top: 1px solid #eee;'/><p>Regards,<br/><strong>ResumeUpdater Team</strong></p></div>");

            return executeMailDispatch(payload.getEmail(), payload.getName(), "Your Updated Resumes are Ready - ThirdEye", htmlBody.toString(), "resumeupdater@thirdeye3.com", "ResumeUpdater");
        } catch (Exception innerEx) {
            log.error("Error formatting SUBMIT notification mail template for user: {}", payload.getEmail(), innerEx);
            return false;
        }
    }
    
    private boolean attemptHistoryEmailSend(MailPayload payload) {
        try {
            StringBuilder htmlBody = new StringBuilder();
            htmlBody.append("<div style='font-family: Arial, sans-serif; padding: 20px; color: #333; line-height: 1.6;'>");
            htmlBody.append("<h2>Secure Access: Your Optimization History Link</h2>");
            htmlBody.append("<p>Hello ").append(payload.getName() != null ? payload.getName() : "User").append(",</p>");
            htmlBody.append("<p>We received a request to review your previously optimized resume packages on ThirdEye.</p>");
            
            htmlBody.append("<div style='margin: 25px 0;'>");
            htmlBody.append("<p>Click the button below to log in and securely access your full dashboard history:</p>");
            htmlBody.append("<a href='").append(payload.getOldResumeUrl()).append("' style='background-color: #4f46e5; color: white; padding: 12px 24px; text-decoration: none; font-weight: bold; border-radius: 8px; display: inline-block; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);' target='_blank'>View History Dashboard</a>");
            htmlBody.append("</div>");

            htmlBody.append("<p style='color: #666; font-size: 13px; margin-top: 20px;'>If the button above does not work, copy and paste this URL into your browser address bar:</p>");
            htmlBody.append("<p style='font-family: monospace; font-size: 12px; background-color: #f8fafc; padding: 10px; border: 1px solid #e2e8f0; border-radius: 6px; word-break: break-all; color: #4f46e5;'>").append(payload.getOldResumeUrl()).append("</p>");
            
            htmlBody.append("<p style='color: #94a3b8; font-size: 12px; margin-top: 25px;'>Security Notice: If you did not request this link, you can safely disregard this message. Your information remains encrypted.</p>");
            htmlBody.append("<br/><hr style='border: 0; border-top: 1px solid #eee;'/><p>Regards,<br/><strong>ThirdEye ResumeHelper Team</strong></p></div>");

            return executeMailDispatch(payload.getEmail(), payload.getName(), "Secure Link: Access Your Resume History - ThirdEye", htmlBody.toString(), "resumeupdater@thirdeye3.com", "ResumeUpdater");
        } catch (Exception innerEx) {
            log.error("Error formatting FETCHOLDHISTORY notification mail template for user: {}", payload.getEmail(), innerEx);
            return false;
        }
    }

    private boolean executeMailDispatch(String toEmail, String toName, String subject, String htmlContent, String fromEmail, String fromEmailName) {
        try {
            MediaType mediaType = MediaType.parse("application/json");
            String cleanHtml = htmlContent.replace("\"", "\\\"");

            String jsonBody = "{\n" +
                    "  \"sender\": {\n" +
                    "     \"name\": \""+fromEmailName+"\",\n" +
                    "     \"email\": \""+fromEmail+"\"\n" +
                    "  },\n" +
                    "  \"to\": [\n" +
                    "    { \"email\": \"" + toEmail + "\", \"name\": \"" + (toName != null ? toName : "User") + "\" }\n" +
                    "  ],\n" +
                    "  \"subject\": \"" + subject + "\",\n" +
                    "  \"htmlContent\": \" " + cleanHtml + " \"\n" +
                    "}";

            RequestBody body = RequestBody.create(jsonBody, mediaType);
            Request request = new Request.Builder()
                    .url("https://api.brevo.com/v3/smtp/email")
                    .post(body)
                    .addHeader("accept", "application/json")
                    .addHeader("api-key", brevoApiKey)
                    .addHeader("content-type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("Notification email ('{}') successfully sent to: {}", subject, toEmail);
                    return true; 
                } else {
                    String resResponse = response.body() != null ? response.body().string() : "";
                    log.warn("Brevo API rejected email delivery to {}. Response: {}", toEmail, resResponse);
                    return false;
                }
            }
        } catch (Exception ex) {
            log.error("Network or transmission error occurred executing mail dispatch execution to user: {}", toEmail, ex);
            return false;
        }
    }
    
    private boolean attemptCourseLinkEmailSend(MailPayload payload) {
        try {
            StringBuilder htmlBody = new StringBuilder();
            htmlBody.append("<div style='font-family: Arial, sans-serif; padding: 20px; color: #333;'>");
            htmlBody.append("<h2>Course Recommendations for ").append(payload.getCompany() != null ? payload.getCompany() : "your application").append("</h2>");
            htmlBody.append("<p>Hello User,</p>");
            htmlBody.append("<p>To help you prepare for your upcoming interview, we have curated a list of important topics based on "+(payload.getCompany() != null ? payload.getCompany() : "Company")+" job description.</p>");
            addPriorityList(htmlBody, "High Priority Topics", payload.getHighPriority(), "#d32f2f");
            addPriorityList(htmlBody, "Medium Priority Topics", payload.getMediumPriority(), "#f57c00");
            addPriorityList(htmlBody, "Low Priority Topics", payload.getLowPriority(), "#388e3c");

            htmlBody.append("<div style='margin-top: 30px;'>");
            htmlBody.append("<p>Click the link below to access your tailored course and start preparing:</p>");
            htmlBody.append("<a href='").append(payload.getCourseUrl()).append("' style='background-color: #2563eb; color: white; padding: 12px 20px; text-decoration: none; border-radius: 5px; font-weight: bold;'>Go to Course</a>");
            htmlBody.append("</div>");

            htmlBody.append("<br/><hr style='border: 0; border-top: 1px solid #eee;'/><p>Regards,<br/><strong>Interview Prep Team</strong></p></div>");

            return executeMailDispatch(payload.getEmail(), "User", "Your Interview Preparation Guide for " + (payload.getCompany() != null ? payload.getCompany() : "Company"), htmlBody.toString(), "interviewprep@thirdeye3.com", "Interview Prep");
        } catch (Exception innerEx) {
            log.error("Error formatting COURSE notification mail template for user: {}", payload.getEmail(), innerEx);
            return false;
        }
    }
    
    private void addPriorityList(StringBuilder sb, String title, List<String> items, String color) {
        if (items != null && !items.isEmpty()) {
            sb.append("<div style='margin-bottom: 20px;'>");
            sb.append("<h3 style='color: ").append(color).append("; margin-bottom: 5px;'>").append(title).append("</h3>");
            sb.append("<table style='width: 100%; border-collapse: collapse; border: 1px solid #ddd;'>");
            for (String item : items) {
                sb.append("<tr>");
                sb.append("<td style='padding: 8px; border-bottom: 1px solid #eee; font-size: 14px;'>").append(item).append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table>");
            sb.append("</div>");
        }
    }

    @RequiredArgsConstructor
    private static class ActivationResult {
        private final Message<MailPayload> message;
        private final boolean success;

        public Message<MailPayload> getMessage() { return message; }
        public boolean isSuccess() { return success; }
    }
}