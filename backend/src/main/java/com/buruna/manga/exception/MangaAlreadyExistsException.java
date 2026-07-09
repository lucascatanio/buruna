package com.buruna.manga.exception;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * Já existe um mangá com o título informado na biblioteca pública. Exceção de domínio pura
 * (ADR-33): {@link DomainErrorType#CONFLICT} → HTTP 409. Usada apenas dentro do manga.
 */
public class MangaAlreadyExistsException extends DomainException {

    public MangaAlreadyExistsException(String title) {
        super(DomainErrorType.CONFLICT, "Já existe um mangá com o título: " + title);
    }
}
