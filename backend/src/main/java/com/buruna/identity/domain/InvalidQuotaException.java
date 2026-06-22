package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.math.BigDecimal;

public final class InvalidQuotaException extends DomainException {

    public InvalidQuotaException(BigDecimal value) {
        super(DomainErrorType.VALIDATION, "Cota inválida: " + value + " (deve ser maior que zero)");
    }
}
