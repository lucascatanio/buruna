package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class InvalidEmailException extends DomainException {

    public InvalidEmailException(String value) {
        super(DomainErrorType.VALIDATION, "E-mail inválido: " + value);
    }
}
