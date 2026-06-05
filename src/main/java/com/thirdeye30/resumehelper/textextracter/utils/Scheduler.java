package com.thirdeye30.resumehelper.textextracter.utils;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.thirdeye30.resumehelper.textextracter.services.MailService;
import com.thirdeye30.resumehelper.textextracter.services.MessageBrokerService;
import com.thirdeye30.resumehelper.textextracter.services.TextExtracterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class Scheduler {
    private volatile boolean isWorking = false;
    private final TextExtracterService textExtracterService;
    private final MailService mailService;

    @Scheduled(fixedRateString = "${thirdeye.message.broker.read.rate}")
    public void readMessagesFromMessageBroker() {
        synchronized (this) {
            if (isWorking) {
                return; 
            }
            isWorking = true;
        }
        try {
            log.info("Starting to read message from broker...");
            textExtracterService.extractTextAndSendToAiProcesser();
            mailService.sendResumeLinkInMail();
        } catch (Exception e) {
            log.error("Error occurred while reading messages: ", e);
        } finally {
            isWorking = false;
        }
    }
}