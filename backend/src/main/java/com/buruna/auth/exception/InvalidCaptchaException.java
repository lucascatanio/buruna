package com.buruna.auth.exception;

import com.buruna.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidCaptchaException extends DomainException {

    public InvalidCaptchaException() {
        super(HttpStatus.BAD_REQUEST, "Captcha inválido ou expirado");
    }
}
