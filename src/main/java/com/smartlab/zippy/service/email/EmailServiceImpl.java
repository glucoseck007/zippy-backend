package com.smartlab.zippy.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Implementation of the EmailService interface.
 * Provides functionality to send both simple text emails and HTML formatted emails.
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender emailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Autowired
    public EmailServiceImpl(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    /**
     * Send a simple text email
     *
     * @param to recipient email address
     * @param subject email subject
     * @param text email body text
     */
    @Override
    @Async
    public void sendSimpleMessage(String to, String subject, String text) {
        try {
            logger.info("Preparing to send simple email to: {} with subject: {}", to, subject);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            logger.info("Email configured, attempting to send via {}:{}",
                       "${spring.mail.host}", "${spring.mail.port}");
            emailSender.send(message);
            logger.info("Email sent successfully to: {} from: {}", to, fromEmail);
        } catch (Exception e) {
            logger.error("Failed to send simple email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Send an HTML formatted email
     *
     * @param to recipient email address
     * @param subject email subject
     * @param htmlContent HTML content for email body
     */
    @Override
    @Async
    public void sendHtmlMessage(String to, String subject, String htmlContent) {
        try {
            logger.info("Preparing to send HTML email to: {} with subject: {}", to, subject);

            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true indicates HTML content

            logger.info("HTML email configured, attempting to send");
            emailSender.send(message);
            logger.info("HTML email sent successfully to: {} from: {}", to, fromEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send HTML email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send HTML email", e);
        }
    }
}
