package com.nexora.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {
    private final JavaMailSender mailSender;
    private final String from;
    private final String appName;

    public EmailVerificationService(JavaMailSender mailSender,
                                    @Value("${nexora.mail.from:${spring.mail.username:}}") String from,
                                    @Value("${spring.application.name:NEXORA}") String appName) {
        this.mailSender = mailSender;
        this.from = from;
        this.appName = appName;
    }

    public void sendOtp(String email, String otp) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("Email sender is not configured. Set NEXORA_MAIL_FROM or spring.mail.username.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(appName + " - Verify your SRMIST email");
            helper.setText("<div style='font-family:Arial,sans-serif;max-width:560px'>"
                    + "<h2>NEXORA email verification</h2>"
                    + "<p>Use the following one-time verification code to create your NEXORA account:</p>"
                    + "<div style='font-size:30px;font-weight:bold;letter-spacing:8px;padding:16px 0'>" + otp + "</div>"
                    + "<p>This code expires in 5 minutes. Do not share it with anyone.</p>"
                    + "<p>If you did not request a NEXORA account, you can ignore this email.</p>"
                    + "</div>", true);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Unable to prepare the verification email. Check the sender and recipient addresses.", ex);
        } catch (MailException ex) {
            throw new IllegalStateException(mailFailureMessage(), ex);
        }
    }

    public void sendLoginOtp(String email, String otp) {
        if (from == null || from.isBlank()) throw new IllegalStateException("Email sender is not configured. Set NEXORA_MAIL_FROM or spring.mail.username.");
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(appName + " - Login verification code");
            helper.setText("<div style='font-family:Arial,sans-serif;max-width:560px'>"
                    + "<h2>NEXORA login verification</h2>"
                    + "<p>Use this one-time code to complete your NEXORA login:</p>"
                    + "<div style='font-size:30px;font-weight:bold;letter-spacing:8px;padding:16px 0'>" + otp + "</div>"
                    + "<p>This code expires in 5 minutes. Do not share it with anyone.</p>"
                    + "<p>If you did not attempt to log in, you can ignore this email.</p>"
                    + "</div>", true);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Unable to prepare the login OTP email. Check the sender and recipient addresses.", ex);
        } catch (MailException ex) {
            throw new IllegalStateException(mailFailureMessage(), ex);
        }
    }

    private String mailFailureMessage() {
        return "NEXORA could not connect to the configured email server. Check your internet/firewall allows outbound SMTP, "
                + "verify NEXORA_MAIL_HOST and NEXORA_MAIL_PORT, and use the provider's SMTP credentials/app password. "
                + "For Gmail, use smtp.gmail.com with port 587 and STARTTLS, or port 465 with SSL enabled. "
                + "No OTP was delivered; please try again after fixing the mail connection.";
    }
}
