package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class InvalidVolumeObjectNameException extends DomainException {

    public InvalidVolumeObjectNameException(String objectName) {
        super(DomainErrorType.VALIDATION, "objectName de upload inválido: " + objectName);
    }
}
