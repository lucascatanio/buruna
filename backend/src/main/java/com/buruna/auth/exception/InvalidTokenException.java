package com.buruna.auth.exception;

import com.buruna.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends DomainException {
    public InvalidTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
    }
}
