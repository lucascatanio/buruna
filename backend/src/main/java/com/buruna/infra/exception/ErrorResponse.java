package com.buruna.infra.exception;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        String timestamp
) {
}
