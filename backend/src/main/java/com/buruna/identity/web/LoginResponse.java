package com.buruna.identity.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code refreshToken} nunca aparece no corpo JSON (ADR-41) — a web layer o lê para
 * montar o cookie httpOnly {@code buruna_refresh} e o descarta.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        boolean requires2FA,
        String tempToken,
        String accessToken,
        @JsonIgnore String refreshToken,
        Long expiresIn
) {
    public static LoginResponse requires2FA(String tempToken) {
        return new LoginResponse(true, tempToken, null, null, null);
    }

    public static LoginResponse authenticated(String accessToken, String refreshToken, long expiresIn) {
        return new LoginResponse(false, null, accessToken, refreshToken, expiresIn);
    }
}
