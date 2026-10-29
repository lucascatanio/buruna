package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * Já existe um mangá com o título informado na biblioteca pública. Exceção de domínio pura
 * (ADR-33): {@link DomainErrorType#CONFLICT} → HTTP 409. Usada apenas dentro do work.
 */
public class WorkAlreadyExistsException extends DomainException {

    public WorkAlreadyExistsException(String title) {
        super(DomainErrorType.CONFLICT, "Já existe um mangá com o título: " + title);
    }
}
