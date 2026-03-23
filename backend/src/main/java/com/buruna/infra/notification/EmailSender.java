package com.buruna.infra.notification;

public interface EmailSender {
    void send(String to, String subject, String body);
}
