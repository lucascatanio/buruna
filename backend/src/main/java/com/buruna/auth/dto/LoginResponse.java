package com.buruna.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        boolean requires2FA,
        String tempToken,
        String accessToken,
        String refreshToken,
        Long expiresIn
) {
    public static LoginResponse requires2FA(String tempToken) {
        return new LoginResponse(true, tempToken, null, null, null);
    }

    public static LoginResponse authenticated(String accessToken, String refreshToken, long expiresIn) {
        return new LoginResponse(false, null, accessToken, refreshToken, expiresIn);
    }
}
