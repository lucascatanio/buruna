package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class UserNotPendingException extends DomainException {
    public UserNotPendingException() {
        super(DomainErrorType.CONFLICT, "User is not in PENDING status");
    }
}
