package com.smartlab.zippy.service.email;

/**
 * Service for handling email operations
 */
public interface EmailService {

    /**
     * Send a simple text email
     *
     * @param to recipient email address
     * @param subject email subject
     * @param text email body text
     */
    void sendSimpleMessage(String to, String subject, String text);

    /**
     * Send an HTML formatted email
     *
     * @param to recipient email address
     * @param subject email subject
     * @param htmlContent HTML content for email body
     */
    void sendHtmlMessage(String to, String subject, String htmlContent);
}
