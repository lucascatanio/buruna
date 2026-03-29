package com.buruna.auth.dto;

public record TotpSetupResponse(
        String secret,
        String qrUri
) {
}
