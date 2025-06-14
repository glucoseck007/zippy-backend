package com.smartlab.zippy.service.auth;

import com.smartlab.zippy.service.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling One-Time Password (OTP) generation, storage, and validation
 */
@Service
public class OtpService {
    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);

    // In-memory storage for OTPs - in production, consider using Redis or another distributed cache
    private final Map<String, OtpData> otpMap = new ConcurrentHashMap<>();

    private final EmailService emailService;

    @Autowired
    public OtpService(EmailService emailService) {
        this.emailService = emailService;
    }

    // OTP configuration
    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 2;

    /**
     * Generate a new OTP for the given email
     * @param email User email
     * @return Generated OTP
     */
    public String generateOtp(String email) {
        String otp = generateRandomOtp();
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        otpMap.put(email, new OtpData(otp, expiryTime));
        logger.debug("OTP generated for email: {}", email);

        return otp;
    }

    /**
     * Validate the provided OTP against the stored value
     * @param email User email
     * @param otp OTP to validate
     * @return true if valid, false otherwise
     */
    public boolean validateOtp(String email, String otp) {
        OtpData otpData = otpMap.get(email);

        if (otpData == null) {
            logger.debug("No OTP found for email: {}", email);
            return false;
        }

        if (LocalDateTime.now().isAfter(otpData.expiryTime)) {
            logger.debug("OTP expired for email: {}", email);
            otpMap.remove(email);
            return false;
        }

        boolean isValid = otpData.otp.equals(otp);

        if (isValid) {
            // Remove OTP after successful validation
            otpMap.remove(email);
            logger.debug("OTP validated successfully for email: {}", email);
        } else {
            logger.debug("Invalid OTP provided for email: {}", email);
        }

        return isValid;
    }

    /**
     * Send the OTP to the user via email
     * @param email User email
     * @param otp OTP to send
     */
    public void sendOtp(String email, String otp) {
        String subject = "Your Verification Code";
        String message = "Your verification code is: " + otp +
                         "\nThis code will expire in " + OTP_EXPIRY_MINUTES + " minutes.";

        // Send an email with the OTP
        emailService.sendSimpleMessage(email, subject, message);

        logger.info("OTP sent to email: {}", email);
    }

    /**
     * Generate a random numeric OTP of the configured length
     * @return Random OTP
     */
    private String generateRandomOtp() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }

        return sb.toString();
    }

    /**
     * Class to store OTP data and expiry time
     */
    private static class OtpData {
        private final String otp;
        private final LocalDateTime expiryTime;

        public OtpData(String otp, LocalDateTime expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
    }
}
