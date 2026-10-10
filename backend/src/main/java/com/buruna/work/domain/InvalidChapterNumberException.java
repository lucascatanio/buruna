package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.math.BigDecimal;

public final class InvalidChapterNumberException extends DomainException {

    public InvalidChapterNumberException(BigDecimal value) {
        super(DomainErrorType.VALIDATION,
                "Número de capítulo inválido: " + value + " (precisa ser >= 0, com até 2 casas decimais)");
    }
}
