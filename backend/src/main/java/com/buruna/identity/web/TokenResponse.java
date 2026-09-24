package com.buruna.identity.web;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * {@code refreshToken} nunca aparece no corpo JSON (ADR-41) — a web layer o lê para
 * montar o cookie httpOnly {@code buruna_refresh} e o descarta.
 */
public record TokenResponse(
        String accessToken,
        @JsonIgnore String refreshToken,
        long expiresIn
) {
}
