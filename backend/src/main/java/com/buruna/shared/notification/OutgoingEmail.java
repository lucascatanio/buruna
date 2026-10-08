package com.buruna.shared.notification;

/** Um e-mail de um lote: cada item tem destinatário, assunto e corpo próprios. */
public record OutgoingEmail(String to, String subject, String body) {
}
