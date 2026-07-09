package com.buruna.engagement.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class ScoreOutOfRangeException extends DomainException {

    public ScoreOutOfRangeException(int value) {
        super(DomainErrorType.VALIDATION, "Score inválido: " + value + " (deve estar entre 1 e 5)");
    }
}
