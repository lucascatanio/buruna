package com.buruna.shared.exception;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        String timestamp
) {
}
