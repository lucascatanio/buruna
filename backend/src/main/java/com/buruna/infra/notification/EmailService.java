package com.buruna.infra.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final EmailSender emailSender;

    public EmailService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async
    public void sendNewRegistrationNotification(String adminEmail, String username, String userEmail) {
        emailSender.send(adminEmail,
                "[Burūna] New registration pending approval",
                "User '%s' (%s) has registered and is awaiting your approval.".formatted(username, userEmail));
    }

    @Async
    public void sendApprovalNotification(String userEmail, String username) {
        emailSender.send(userEmail,
                "[Burūna] Your account has been approved",
                "Hello %s! Your account has been approved. You can now log in.".formatted(username));
    }

    @Async
    public void sendRejectionNotification(String userEmail, String username, String reason) {
        String body = (reason != null && !reason.isBlank())
                ? "Hello %s, your registration was not approved. Reason: %s".formatted(username, reason)
                : "Hello %s, your registration was not approved.".formatted(username);
        emailSender.send(userEmail, "[Burūna] Registration status update", body);
    }

    @Async
    public void sendFeedbackNotification(String adminEmail, String username, String userEmail,
                                         String message, String timestamp) {
        String body = "Feedback received from %s (%s) at %s:\n\n%s".formatted(
                username, userEmail, timestamp, message);
        emailSender.send(adminEmail, "[Burūna] Feedback from " + username, body);
    }

    @Async
    public void sendInactivityWarning(String userEmail, String username) {
        emailSender.send(userEmail,
                "[Burūna] Inactivity warning",
                "Hello %s, your account will be deactivated in 15 days due to inactivity.".formatted(username));
    }
}
