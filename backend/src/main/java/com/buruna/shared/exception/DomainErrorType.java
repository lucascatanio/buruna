package com.buruna.shared.exception;

/**
 * Categoria de erro de domínio — conceito do próprio domínio, independente de
 * framework web. A tradução para status HTTP acontece num único lugar
 * (GlobalExceptionHandler), mantendo o domínio livre de HTTP (ADR-33).
 */
public enum DomainErrorType {
    NOT_FOUND,
    CONFLICT,
    FORBIDDEN,
    UNAUTHORIZED,
    VALIDATION
}
