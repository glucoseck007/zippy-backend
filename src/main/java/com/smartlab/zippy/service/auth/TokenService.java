package com.smartlab.zippy.service.auth;

import com.smartlab.zippy.config.JwtConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtConfig jwtConfig;

    public TokenService(RedisTemplate<String, Object> redisTemplate, JwtConfig jwtConfig) {
        this.redisTemplate = redisTemplate;
        this.jwtConfig = jwtConfig;
        // Verify Redis connectivity on startup
        verifyRedisConnection();
    }

    /**
     * Verify that Redis is connected and operational
     */
    private void verifyRedisConnection() {
        try {
            String testKey = "test:connection";
            String testValue = "connected";

            // Test set operation
            log.info("Testing Redis connection by setting key: {}", testKey);
            redisTemplate.opsForValue().set(testKey, testValue);

            // Verify key exists after setting
            Boolean keyExists = redisTemplate.hasKey(testKey);
            log.info("Key exists check: {}", keyExists);

            // Test get operation
            String result = (String) redisTemplate.opsForValue().get(testKey);
            log.info("Redis get test result: {}", result);

            if (testValue.equals(result) && Boolean.TRUE.equals(keyExists)) {
                log.info("Redis connection verified successfully");
            } else {
                log.error("Redis connection test failed: value mismatch or key not found. Expected: {}, Got: {}, Key exists: {}",
                          testValue, result, keyExists);
            }

            // Clean up test key
            redisTemplate.delete(testKey);

            // Double check key was deleted
            Boolean keyStillExists = redisTemplate.hasKey(testKey);
            log.info("Key deleted check: {}", !Boolean.TRUE.equals(keyStillExists));
        } catch (Exception e) {
            log.error("Redis connection verification failed", e);
        }
    }

    /**
     * Store a refresh token in Redis with username as a reference
     * @param username The username associated with the token
     * @return A UUID refresh token
     */
    public String generateRefreshToken(String username) {
        String token = UUID.randomUUID().toString();
        String key = JwtConfig.REFRESH_TOKEN_PREFIX + token;

        try {
            log.info("Storing refresh token in Redis with key: {}", key);

            // Store username as the value with the refresh token as the key
            redisTemplate.opsForValue().set(key, username);
            redisTemplate.expire(key, jwtConfig.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);

            // Verify the token was stored correctly
            Boolean keyExists = redisTemplate.hasKey(key);
            String storedUsername = (String) redisTemplate.opsForValue().get(key);

            log.info("Verification - Key exists: {}, Retrieved username: {}", keyExists, storedUsername);

            if (!Boolean.TRUE.equals(keyExists) || storedUsername == null) {
                log.error("Failed to store refresh token in Redis - key exists: {}, value: {}", keyExists, storedUsername);
            }

            // Verify expiration was set
            Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            log.info("Token expiration set to {} ms", ttl);

        } catch (Exception e) {
            log.error("Error storing refresh token in Redis: {}", e.getMessage(), e);
        }

        return token;
    }

    /**
     * Validate a refresh token from Redis
     * @param token The refresh token to validate
     * @return The username associated with the token or null if invalid
     */
    public String validateRefreshToken(String token) {
        String key = JwtConfig.REFRESH_TOKEN_PREFIX + token;
        try {
            log.info("Validating refresh token with key: {}", key);

            // First check if key exists
            Boolean keyExists = redisTemplate.hasKey(key);
            log.info("Refresh token key exists: {}", keyExists);

            if (Boolean.TRUE.equals(keyExists)) {
                String username = (String) redisTemplate.opsForValue().get(key);
                log.info("Validation result for token: value={}", username);
                return username;
            } else {
                log.warn("Refresh token not found in Redis: {}", key);
                return null;
            }
        } catch (Exception e) {
            log.error("Error validating refresh token: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Revoke a refresh token
     * @param token The refresh token to revoke
     */
    public void revokeRefreshToken(String token) {
        String key = JwtConfig.REFRESH_TOKEN_PREFIX + token;
        redisTemplate.delete(key);
    }

    /**
     * Blacklist an access token
     * @param token The JWT access token to blacklist
     */
    public void blacklistAccessToken(String token) {
        String key = JwtConfig.BLACKLISTED_TOKEN_PREFIX + token;
        redisTemplate.opsForValue().set(key, "blacklisted");

        // Set expiration to match the token's remaining lifetime
        // Default to 15 minutes (900 seconds) if we can't determine the remaining time
        redisTemplate.expire(key, 15 * 60, TimeUnit.SECONDS);
    }

    /**
     * Check if an access token is blacklisted
     * @param token The JWT access token to check
     * @return true if the token is blacklisted, false otherwise
     */
    public boolean isAccessTokenBlacklisted(String token) {
        String key = JwtConfig.BLACKLISTED_TOKEN_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Revoke all tokens for a user by username
     * To implement this properly with Redis, we would need to maintain a secondary
     * index mapping usernames to their tokens
     *
     * @param username The username whose tokens should be revoked
     */
    public void revokeAllUserTokens(String username) {
        // For a complete implementation, you would store a mapping of username -> tokens
        // and then iterate through and delete each one
        // For now, this is a placeholder for that functionality
    }
}
