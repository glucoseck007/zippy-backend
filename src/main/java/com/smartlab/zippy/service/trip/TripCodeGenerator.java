package com.smartlab.zippy.service.trip;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class TripCodeGenerator {
    
    private static final String PREFIX = "T-";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();
    
    public String generateTripCode() {
        StringBuilder codeBuilder = new StringBuilder(PREFIX);
        
        for (int i = 0; i < CODE_LENGTH; i++) {
            codeBuilder.append(random.nextInt(10));
        }
        
        return codeBuilder.toString();
    }
}
