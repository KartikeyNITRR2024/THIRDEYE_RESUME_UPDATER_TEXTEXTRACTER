package com.thirdeye30.resumehelper.textextracter.services.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.thirdeye30.resumehelper.textextracter.dtos.AlProcesserPayload;
import com.thirdeye30.resumehelper.textextracter.dtos.Message;
import com.thirdeye30.resumehelper.textextracter.dtos.StatusResume;
import com.thirdeye30.resumehelper.textextracter.dtos.TextExtracterPayload;
import com.thirdeye30.resumehelper.textextracter.enums.Status;
import com.thirdeye30.resumehelper.textextracter.services.MessageBrokerService;
import com.thirdeye30.resumehelper.textextracter.services.PdfDownloaderService;
import com.thirdeye30.resumehelper.textextracter.services.TextExtracterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@Service
@RequiredArgsConstructor
@Slf4j
public class TextExtracterServiceImpl implements TextExtracterService {

	
	private final MessageBrokerService messageBrokerService;
	private final PdfDownloaderService pdfDownloaderService;
	
	private String extractTextFromBytes(byte[] pdfBytes) {
	    if (pdfBytes == null || pdfBytes.length == 0) {
	        return "";
	    }
	    
	    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
	        if (!document.isEncrypted()) {
	            PDFTextStripper stripper = new PDFTextStripper();
	            return stripper.getText(document);
	        } else {
	            log.warn("PDF is encrypted internally even after S3 decryption.");
	            return "";
	        }
	    } catch (Exception e) {
	        log.error("Failed to extract text from PDF bytes", e);
	        return "";
	    }
	}
	
	@Override
	public void extractTextAndSendToAiProcesser() {
	    while(true) {
	        try {
	            List<Message<TextExtracterPayload>> messages = messageBrokerService.getTextExtracterPayloadMessage("textextracter");
	            if(messages.isEmpty()) {
	                break;
	            }

	            List<AlProcesserPayload> payloads = new ArrayList<>();
	            List<StatusResume> statusPayloads = new ArrayList<>();
	            
	            for(Message<TextExtracterPayload> message : messages) {
	                TextExtracterPayload payload = message.getMessage();
	                byte[] bytes = pdfDownloaderService.downloadAndDecrypt(payload.getAwsPath(), payload.getEncryptionkey());
	                
	                if(bytes != null) {
	                    String extractedText = extractTextFromBytes(bytes);
	                    if (!extractedText.isEmpty()) {
	                        log.info("Successfully extracted {} characters", extractedText.length());
	                        payloads.add(new AlProcesserPayload(payload.getResumeId(),payload.getResumeMetadata(), extractedText));
	                        statusPayloads.add(new StatusResume(payload.getResumeId(),Status.EXTRACTING_TEXT));
	                    }
	                }
	            }
	            log.info("Pushing {} resumes to ai processing", payloads.size());
                messageBrokerService.sendMessages("aiprocesser", payloads);
                messageBrokerService.sendMessages("statusupdater", statusPayloads);
	        } catch (Exception ex) {
	            log.error("Error in text extraction loop", ex);
	            break;
	        }
	    }
	}

}
