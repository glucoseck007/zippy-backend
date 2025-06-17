package com.smartlab.zippy.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class JwtConfig {
    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.access-token.expiration}")
    private long accessTokenExpiration;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    public static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    public static final String BLACKLISTED_TOKEN_PREFIX = "blacklisted_token:";
}
