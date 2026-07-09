package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class UserNotActiveException extends DomainException {
    public UserNotActiveException(String message) {
        super(DomainErrorType.FORBIDDEN, message);
    }
}
