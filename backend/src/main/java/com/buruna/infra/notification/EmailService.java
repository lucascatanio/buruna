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

    @Async
    public void sendPasswordResetEmail(String userEmail, String username, String resetLink) {
        emailSender.send(userEmail,
                "[Burūna] Password reset",
                """
                Hello %s,

                We received a request to reset your password. Click the link below to set a new password:

                %s

                This link expires in 1 hour. If you didn't request this, you can safely ignore this email.
                """.formatted(username, resetLink));
    }

    @Async
    public void sendMangaSubmissionNotification(String adminEmail, String submitterUsername, String mangaTitle) {
        emailSender.send(adminEmail,
                "[Burūna] New manga submission pending approval",
                "User '%s' has submitted '%s' for publication and is awaiting your approval."
                        .formatted(submitterUsername, mangaTitle));
    }

    @Async
    public void sendMangaApprovalNotification(String userEmail, String mangaTitle) {
        emailSender.send(userEmail,
                "[Burūna] Your manga has been approved",
                "Your manga '%s' has been approved and is now visible in the public library."
                        .formatted(mangaTitle));
    }

    @Async
    public void sendMangaRejectionNotification(String userEmail, String mangaTitle, String reason) {
        String body = (reason != null && !reason.isBlank())
                ? "Your manga '%s' was not approved. Reason: %s".formatted(mangaTitle, reason)
                : "Your manga '%s' was not approved.".formatted(mangaTitle);
        emailSender.send(userEmail, "[Burūna] Manga submission update", body);
    }
}
