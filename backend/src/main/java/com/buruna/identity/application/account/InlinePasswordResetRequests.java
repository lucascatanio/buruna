package com.buruna.identity.application.account;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Dev/local não tem Pub/Sub: o pedido é processado direto, na própria requisição (ADR-42). */
@Component
@Profile("local")
public class InlinePasswordResetRequests implements PasswordResetRequests {

    private final ProcessPasswordResetRequestUseCase processPasswordResetRequest;

    public InlinePasswordResetRequests(ProcessPasswordResetRequestUseCase processPasswordResetRequest) {
        this.processPasswordResetRequest = processPasswordResetRequest;
    }

    @Override
    public void submit(String email) {
        processPasswordResetRequest.handle(email);
    }
}
