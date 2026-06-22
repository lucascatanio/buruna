package com.buruna.identity.domain;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

public class InvalidCaptchaException extends LegacyHttpDomainException {

    public InvalidCaptchaException() {
        super(HttpStatus.BAD_REQUEST, "Captcha inválido ou expirado");
    }
}
