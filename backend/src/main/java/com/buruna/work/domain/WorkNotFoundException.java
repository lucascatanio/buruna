package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

/**
 * Mangá inexistente (ou não visível no contexto pedido). Exceção de domínio pura (ADR-33):
 * {@link DomainErrorType#NOT_FOUND} → HTTP 404. Usada apenas dentro do contexto work.
 */
public class WorkNotFoundException extends DomainException {

    public WorkNotFoundException(UUID id) {
        super(DomainErrorType.NOT_FOUND, "Mangá não encontrado com id: " + id);
    }

    public WorkNotFoundException(String slug) {
        super(DomainErrorType.NOT_FOUND, "Mangá não encontrado com slug: " + slug);
    }
}
