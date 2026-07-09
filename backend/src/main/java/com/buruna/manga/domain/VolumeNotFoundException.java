package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class VolumeNotFoundException extends DomainException {

    public VolumeNotFoundException(UUID id) {
        super(DomainErrorType.NOT_FOUND, "Volume não encontrado: " + id);
    }
}
