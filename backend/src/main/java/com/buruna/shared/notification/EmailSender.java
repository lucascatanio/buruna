package com.buruna.shared.notification;

import java.util.List;

public interface EmailSender {
    void send(String to, String subject, String body);

    /** Um e-mail individual por destinatário — nenhum vê o endereço dos outros. */
    default void sendToEach(List<String> recipients, String subject, String body) {
        recipients.forEach(to -> send(to, subject, body));
    }
}
