package com.thirdeye30.resumehelper.textextracter.services;

import java.util.List;

import com.thirdeye30.resumehelper.textextracter.dtos.Message;
import com.thirdeye30.resumehelper.textextracter.dtos.TextExtracterPayload;

public interface MessageBrokerService {

	void sendMessages(String topicName, Object messagess);

	List<Message<TextExtracterPayload>> getMessage(String topicName);

}

