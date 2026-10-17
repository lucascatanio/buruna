package com.buruna.sourcing.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class TrackedWorkNotFoundException extends DomainException {

    public TrackedWorkNotFoundException(UUID id) {
        super(DomainErrorType.NOT_FOUND, "Obra acompanhada não encontrada: " + id);
    }
}
