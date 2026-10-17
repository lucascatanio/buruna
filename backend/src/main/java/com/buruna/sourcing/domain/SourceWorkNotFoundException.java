package com.buruna.sourcing.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class SourceWorkNotFoundException extends DomainException {

    public SourceWorkNotFoundException(String externalId) {
        super(DomainErrorType.NOT_FOUND, "Obra não encontrada no MangaDex: " + externalId);
    }
}
