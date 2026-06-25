package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class InvalidVolumeNumberException extends DomainException {

    public InvalidVolumeNumberException(int value) {
        super(DomainErrorType.VALIDATION, "Número de volume inválido: " + value + " (deve ser >= 1)");
    }
}
