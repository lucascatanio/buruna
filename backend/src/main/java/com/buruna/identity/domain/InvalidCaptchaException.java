package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class InvalidCaptchaException extends DomainException {

    public InvalidCaptchaException() {
        super(DomainErrorType.VALIDATION, "Captcha inválido ou expirado");
    }
}
