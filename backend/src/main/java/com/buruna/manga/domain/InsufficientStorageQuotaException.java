package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.math.BigDecimal;

/**
 * Cota de armazenamento estourada. Exceção de domínio pura (ADR-33): a categoria
 * {@link DomainErrorType#UNPROCESSABLE} é traduzida para HTTP 422 no
 * GlobalExceptionHandler. Corrige o bug latente em que o {@code @ResponseStatus(422)}
 * da exceção legada era engolido pelo {@code @ExceptionHandler(Exception.class)},
 * resultando em 500.
 */
public final class InsufficientStorageQuotaException extends DomainException {

    public InsufficientStorageQuotaException(BigDecimal quotaGb, long usedBytes, long additionalBytes) {
        super(DomainErrorType.UNPROCESSABLE, String.format(
                "Cota insuficiente. Cota: %.2f GB, utilizado: %.2f GB, arquivo: %.2f MB",
                quotaGb.doubleValue(),
                usedBytes / (1024.0 * 1024.0 * 1024.0),
                additionalBytes / (1024.0 * 1024.0)
        ));
    }
}
