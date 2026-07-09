package com.buruna.engagement.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class MangaNotFoundException extends DomainException {

    public MangaNotFoundException(UUID mangaId) {
        super(DomainErrorType.NOT_FOUND, "Mangá público não encontrado: " + mangaId);
    }
}
