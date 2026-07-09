package com.buruna.shared.notification;

public interface EmailSender {
    void send(String to, String subject, String body);
}
