package com.buruna.identity.application.account;

/**
 * Ponto de entrada do pedido de reset de senha. Publica no Pub/Sub em produção
 * ({@link PubSubPasswordResetRequests}) ou processa direto em dev/local
 * ({@link InlinePasswordResetRequests}) — o tempo de resposta do endpoint não pode
 * depender de o e-mail existir ou não (ADR-42).
 */
public interface PasswordResetRequests {

    void submit(String email);
}
