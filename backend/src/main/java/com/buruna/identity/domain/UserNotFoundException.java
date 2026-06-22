package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class UserNotFoundException extends DomainException {
    public UserNotFoundException(UUID id) {
        super(DomainErrorType.NOT_FOUND, "User not found: " + id);
    }
}
