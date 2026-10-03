package com.buruna.identity.application.account;

/** Corpo publicado no tópico Pub/Sub de reset de senha e recebido pelo push endpoint (ADR-42). */
public record PasswordResetMessage(String email) {
}
