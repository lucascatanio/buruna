package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * Lançada quando um código TOTP correto, mas já aceito num passo de tempo
 * anterior ou igual, é reapresentado (replay) dentro da mesma janela de 30s
 * (FIND-004). Mesmo status de credencial inválida (401) que um código errado —
 * o valor já não serve mais para autenticar de novo.
 */
public final class TotpReplayException extends DomainException {

    public TotpReplayException() {
        super(DomainErrorType.UNAUTHORIZED, "TOTP code has already been used");
    }
}
