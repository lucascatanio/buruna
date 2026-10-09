package com.buruna.shared.notification;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailService {

    private final EmailSender emailSender;

    public EmailService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void sendNewRegistrationNotification(List<String> adminEmails, String username, String userEmail) {
        emailSender.sendToEach(adminEmails,
                "[Burūna] New registration pending approval",
                "User '%s' (%s) has registered and is awaiting your approval.".formatted(username, userEmail));
    }

    public void sendExistingAccountNotice(String userEmail, String username,
                                          String loginLink, String forgotPasswordLink) {
        emailSender.send(userEmail,
                "[Burūna] Sign-up attempt with your email",
                """
                Hello %s,

                Someone tried to create a new Burūna account with this email address, but it \
                already belongs to your account.

                If it was you, log in at %s or reset your password at %s.

                If it wasn't you, you can ignore this email: nothing has changed in your account.
                """.formatted(username, loginLink, forgotPasswordLink));
    }

    public void sendApprovalNotification(String userEmail, String username) {
        emailSender.send(userEmail,
                "[Burūna] Your account has been approved",
                "Hello %s! Your account has been approved. You can now log in.".formatted(username));
    }

    public void sendRejectionNotification(String userEmail, String username, String reason) {
        String body = (reason != null && !reason.isBlank())
                ? "Hello %s, your registration was not approved. Reason: %s".formatted(username, reason)
                : "Hello %s, your registration was not approved.".formatted(username);
        emailSender.send(userEmail, "[Burūna] Registration status update", body);
    }

    public void sendFeedbackNotification(List<String> adminEmails, String username, String userEmail,
                                         String message, String timestamp) {
        String body = "Feedback received from %s (%s) at %s:\n\n%s".formatted(
                username, userEmail, timestamp, message);
        emailSender.sendToEach(adminEmails, "[Burūna] Feedback from " + username, body);
    }

    /** Um lote só para todos os avisados, cada e-mail com o nome do próprio usuário. */
    public void sendInactivityWarnings(List<EmailRecipient> recipients) {
        emailSender.sendBatch(recipients.stream()
                .map(r -> new OutgoingEmail(r.email(),
                        "[Burūna] Inactivity warning",
                        "Hello %s, your account will be deactivated in 15 days due to inactivity."
                                .formatted(r.username())))
                .toList());
    }

    public void sendPasswordResetEmail(String userEmail, String username, String resetLink) {
        emailSender.sendOrFail(userEmail,
                "[Burūna] Password reset",
                """
                Hello %s,

                We received a request to reset your password. Click the link below to set a new password:

                %s

                This link expires in 1 hour. If you didn't request this, you can safely ignore this email.
                """.formatted(username, resetLink));
    }

    public void sendWorkSubmissionNotification(List<String> adminEmails, String submitterUsername, String workTitle) {
        emailSender.sendToEach(adminEmails,
                "[Burūna] New manga submission pending approval",
                "User '%s' has submitted '%s' for publication and is awaiting your approval."
                        .formatted(submitterUsername, workTitle));
    }

    public void sendWorkApprovalNotification(String userEmail, String workTitle) {
        emailSender.send(userEmail,
                "[Burūna] Your manga has been approved",
                "Your manga '%s' has been approved and is now visible in the public library."
                        .formatted(workTitle));
    }

    public void sendWorkRejectionNotification(String userEmail, String workTitle, String reason) {
        String body = (reason != null && !reason.isBlank())
                ? "Your manga '%s' was not approved. Reason: %s".formatted(workTitle, reason)
                : "Your manga '%s' was not approved.".formatted(workTitle);
        emailSender.send(userEmail, "[Burūna] Manga submission update", body);
    }
}
