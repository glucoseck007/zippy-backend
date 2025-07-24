package com.smartlab.zippy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {
    private String broker;
    private String clientId;
    private String username;
    private String password;
    private List<String> inboundTopics;
    private List<String> outboundTopics;
    private int qos;
}
