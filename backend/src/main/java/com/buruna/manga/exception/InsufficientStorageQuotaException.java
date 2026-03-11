package com.buruna.manga.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.math.BigDecimal;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientStorageQuotaException extends RuntimeException {

    public InsufficientStorageQuotaException(BigDecimal quotaGb, long usedBytes, long additionalBytes) {
        super(String.format(
                "Cota insuficiente. Cota: %.2f GB, utilizado: %.2f GB, arquivo: %.2f MB",
                quotaGb.doubleValue(),
                usedBytes / (1024.0 * 1024.0 * 1024.0),
                additionalBytes / (1024.0 * 1024.0)
        ));
    }
}