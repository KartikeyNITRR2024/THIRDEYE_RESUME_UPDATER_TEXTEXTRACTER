package com.thirdeye30.resumehelper.textextracter.services.impl;

import com.thirdeye30.resumehelper.textextracter.dtos.Message;
import com.thirdeye30.resumehelper.textextracter.dtos.ResumeMailPayload;
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
    public void sendResumeLinkInMail() {
        List<Message<ResumeMailPayload>> messagesToProcess = new ArrayList<>();

        while (true) {
            try {
                List<Message<ResumeMailPayload>> messages = messageBrokerService.getResumeMailPayloadMessage("mailprocesser");
                if (messages == null || messages.isEmpty()) {
                    break;
                }
                for (Message<ResumeMailPayload> message : messages) {
                    if (message != null && message.getMessage() != null) {
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

        List<Message<ResumeMailPayload>> failedMessages = messagesToProcess;
        int maxTries = 3;

        for (int attempt = 1; attempt <= maxTries && !failedMessages.isEmpty(); attempt++) {
            if (attempt > 1) {
                log.info("Starting retry attempt #{} for {} failed emails via 5-thread pool...", attempt, failedMessages.size());
            } else {
                log.info("Processing {} emails concurrently using up to 5 worker threads...", failedMessages.size());
            }

            List<CompletableFuture<ActivationResult>> futures = failedMessages.stream()
                .map(msg -> CompletableFuture.supplyAsync(() -> {
                    boolean success = attemptEmailSend(msg.getMessage());
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
            log.error("{} emails completely failed to send after exhausting all {} async attempts.", failedMessages.size(), maxTries);
        }
    }

    private boolean attemptEmailSend(ResumeMailPayload payload) {
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

            MediaType mediaType = MediaType.parse("application/json");
            String cleanHtml = htmlBody.toString().replace("\"", "\\\"");

            String jsonBody = "{\n" +
                    "  \"sender\": {\n" +
                    "     \"name\": \"ResumeUpdater\",\n" +
                    "     \"email\": \"resumeupdater@thirdeye3.com\"\n" +
                    "  },\n" +
                    "  \"to\": [\n" +
                    "    { \"email\": \"" + payload.getEmail() + "\", \"name\": \"" + (payload.getName() != null ? payload.getName() : "") + "\" }\n" +
                    "  ],\n" +
                    "  \"subject\": \"Your Updated Resumes are Ready - ThirdEye\",\n" +
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
                    log.info("Resume notification email successfully sent to: {}", payload.getEmail());
                    return true; 
                } else {
                    String resResponse = response.body() != null ? response.body().string() : "";
                    log.warn("Brevo API rejected email delivery to {}. Response: {}", payload.getEmail(), resResponse);
                    return false;
                }
            }
        } catch (Exception innerEx) {
            log.error("Network or processing error occurred executing mail dispatch to user: {}", payload.getEmail(), innerEx);
            return false;
        }
    }

    @RequiredArgsConstructor
    private static class ActivationResult {
        private final Message<ResumeMailPayload> message;
        private final boolean success;

        public Message<ResumeMailPayload> getMessage() { return message; }
        public boolean isSuccess() { return success; }
    }
}