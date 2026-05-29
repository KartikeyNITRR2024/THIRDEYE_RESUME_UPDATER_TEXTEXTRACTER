package com.thirdeye30.resumehelper.textextracter.configs;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.thirdeye30.resumehelper.textextracter.dtos.Topic;

import lombok.Data;



@Component
@ConfigurationProperties(prefix = "thirdeye.messagebroker")
@Data
public class MessageBrokerConfig {
    private Map<String, Topic> topics = new HashMap<>(); 
}
