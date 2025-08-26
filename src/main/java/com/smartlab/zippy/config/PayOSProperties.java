package com.smartlab.zippy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "payos")
@Data
public class PayOSProperties {
    private String clientId;
    private String apiKey;
    private String checksumKey;
    private String baseUrl;
}
