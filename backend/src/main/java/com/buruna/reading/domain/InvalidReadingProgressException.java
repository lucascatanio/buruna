package com.buruna.reading.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class InvalidReadingProgressException extends DomainException {

    public InvalidReadingProgressException(String message) {
        super(DomainErrorType.VALIDATION, message);
    }
}
