package com.thirdeye30.resumehelper.textextracter.externalcontollers;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.thirdeye30.resumehelper.textextracter.dtos.MailPayload;
import com.thirdeye30.resumehelper.textextracter.dtos.Message;
import com.thirdeye30.resumehelper.textextracter.dtos.TextExtracterPayload;

@FeignClient(
		name = "MESSAGEBROKER"
)
public interface MessageBrokerClient {
	
	@GetMapping("/messagebroker/message/multiple/{topicname}/{topickey}/{count}")
	ResponseEntity<List<Message<TextExtracterPayload>>> getTextExtracterPayloadMessages(
        @PathVariable("topicname") String topicName,
        @PathVariable("topickey") String topicKey,
        @PathVariable("count") Long count
    );
	
	@GetMapping("/messagebroker/message/multiple/{topicname}/{topickey}/{count}")
	ResponseEntity<List<Message<MailPayload>>> getResumeMailPayloadMessages(
        @PathVariable("topicname") String topicName,
        @PathVariable("topickey") String topicKey,
        @PathVariable("count") Long count
    );

    @PostMapping("/messagebroker/message/multiple/{topicname}/{topickey}")
    ResponseEntity<String> setMessages(
            @PathVariable("topicname") String topicName,
            @PathVariable("topickey") String topicKey,
            @RequestBody Object messages
    );
}
