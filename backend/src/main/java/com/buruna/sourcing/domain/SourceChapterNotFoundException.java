package com.buruna.sourcing.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class SourceChapterNotFoundException extends DomainException {

    public SourceChapterNotFoundException(UUID id) {
        super(DomainErrorType.NOT_FOUND, "Versão de capítulo não encontrada: " + id);
    }
}
