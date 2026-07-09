package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * Um ou mais volumes já existem na biblioteca pública (hash duplicado). Exceção de domínio
 * pura (ADR-33): {@link DomainErrorType#CONFLICT} → HTTP 409.
 */
public final class PublicVolumeConflictException extends DomainException {

    public PublicVolumeConflictException() {
        super(DomainErrorType.CONFLICT, "Um ou mais volumes já existem na biblioteca pública");
    }
}
