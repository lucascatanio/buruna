package com.buruna.shared.notification;

/** Destinatário de um e-mail personalizado: o endereço e o nome usado na saudação. */
public record EmailRecipient(String email, String username) {
}
