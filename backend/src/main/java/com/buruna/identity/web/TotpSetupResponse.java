package com.buruna.identity.web;

public record TotpSetupResponse(
        String secret,
        String qrUri
) {
}
