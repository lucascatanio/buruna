package com.buruna.sourcing.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/** Ação do ADM que não cabe no estado atual da versão ou da obra acompanhada. */
public final class InvalidSourceActionException extends DomainException {

    public InvalidSourceActionException(String message) {
        super(DomainErrorType.UNPROCESSABLE, message);
    }
}
