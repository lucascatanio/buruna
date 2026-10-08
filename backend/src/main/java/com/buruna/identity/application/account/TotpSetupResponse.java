package com.buruna.identity.application.account;

public record TotpSetupResponse(
        String secret,
        String qrUri
) {
}
