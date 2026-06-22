package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class UserAlreadyExistsException extends DomainException {
    public UserAlreadyExistsException(String field) {
        super(DomainErrorType.CONFLICT, "Already exists an user with this " + field);
    }
}
