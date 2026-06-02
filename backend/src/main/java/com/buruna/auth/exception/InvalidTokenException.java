package com.buruna.auth.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends LegacyHttpDomainException {
    public InvalidTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
    }
}
