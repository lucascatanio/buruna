package com.buruna.manga.exception;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public class VolumeAccessDeniedException extends DomainException {

    public VolumeAccessDeniedException(UUID volumeId) {
        super(DomainErrorType.FORBIDDEN, "Acesso negado ao volume: " + volumeId);
    }
}
