package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class InvalidTokenException extends DomainException {
    public InvalidTokenException() {
        super(DomainErrorType.UNAUTHORIZED, "Invalid or expired token");
    }
}
