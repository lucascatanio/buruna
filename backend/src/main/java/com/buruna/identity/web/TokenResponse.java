package com.buruna.identity.web;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
