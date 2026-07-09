package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * Já existe um mangá com o mesmo título na biblioteca pública. Exceção de domínio pura
 * (ADR-33): {@link DomainErrorType#CONFLICT} → HTTP 409.
 */
public final class PublicTitleConflictException extends DomainException {

    public PublicTitleConflictException(String title) {
        super(DomainErrorType.CONFLICT,
                "Já existe um mangá com este título na biblioteca pública: " + title);
    }
}
