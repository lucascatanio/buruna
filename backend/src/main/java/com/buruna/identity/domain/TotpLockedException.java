package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * Lançada quando o TOTP do usuário está temporariamente bloqueado por excesso
 * de tentativas inválidas (FIND-004). Traduzida para 429 no
 * {@code GlobalExceptionHandler} via {@link DomainErrorType#RATE_LIMITED}.
 */
public final class TotpLockedException extends DomainException {

    public TotpLockedException() {
        super(DomainErrorType.RATE_LIMITED, "Too many invalid TOTP attempts. Try again later.");
    }
}
