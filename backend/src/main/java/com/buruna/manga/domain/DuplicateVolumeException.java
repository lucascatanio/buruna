package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class DuplicateVolumeException extends DomainException {

    public DuplicateVolumeException() {
        super(DomainErrorType.CONFLICT, "Este arquivo já foi enviado anteriormente (hash duplicado)");
    }

    public DuplicateVolumeException(int volumeNumber) {
        super(DomainErrorType.CONFLICT, "Já existe o volume " + volumeNumber + " para este mangá");
    }
}
