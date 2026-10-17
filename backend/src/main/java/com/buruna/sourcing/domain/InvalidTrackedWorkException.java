package com.buruna.sourcing.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class InvalidTrackedWorkException extends DomainException {

    public InvalidTrackedWorkException(String message) {
        super(DomainErrorType.VALIDATION, message);
    }
}
