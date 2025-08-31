package com.smartlab.zippy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import vn.payos.PayOS;

@Configuration
@RequiredArgsConstructor
public class PayOSConfig {

    private final PayOSProperties payOSProperties;

    @Bean
    public PayOS payOS() {
        return new PayOS(
            payOSProperties.getClientId(),
            payOSProperties.getApiKey(),
            payOSProperties.getChecksumKey()
        );
    }
}
