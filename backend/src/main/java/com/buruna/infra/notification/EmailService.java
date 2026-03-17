package com.buruna.infra.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@localhost}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendNewRegistrationNotification(String adminEmail, String username, String userEmail) {
        send(adminEmail,
                "[Burūna] New registration pending approval",
                "User '%s' (%s) has registered and is awaiting your approval.".formatted(username, userEmail));
    }

    @Async
    public void sendApprovalNotification(String userEmail, String username) {
        send(userEmail,
                "[Burūna] Your account has been approved",
                "Hello %s! Your account has been approved. You can now log in.".formatted(username));
    }

    @Async
    public void sendRejectionNotification(String userEmail, String username, String reason) {
        String body = (reason != null && !reason.isBlank())
                ? "Hello %s, your registration was not approved. Reason: %s".formatted(username, reason)
                : "Hello %s, your registration was not approved.".formatted(username);
        send(userEmail, "[Burūna] Registration status update", body);
    }

    @Async
    public void sendInactivityWarning(String userEmail, String username) {
        send(userEmail,
                "[Burūna] Inactivity warning",
                "Hello %s, your account will be deactivated in 15 days due to inactivity.".formatted(username));
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
