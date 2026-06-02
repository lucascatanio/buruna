package com.buruna.shared.exception;

/**
 * Base das exceções de domínio — pura, sem dependência de framework web (ADR-33).
 * Carrega apenas a mensagem e uma {@link DomainErrorType} (categoria de domínio);
 * a tradução para status HTTP acontece exclusivamente no GlobalExceptionHandler.
 *
 * <p>Subclasses concretas e nomeadas por intenção (ex.: MangaNotFoundException)
 * são criadas em cada contexto durante a migração.
 */
public abstract class DomainException extends RuntimeException {

    private final DomainErrorType errorType;

    protected DomainException(DomainErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public DomainErrorType errorType() {
        return errorType;
    }
}
