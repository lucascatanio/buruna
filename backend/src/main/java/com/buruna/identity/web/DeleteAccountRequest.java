package com.buruna.identity.web;

import jakarta.validation.constraints.NotBlank;

// totpCode só é exigido (e conferido) quando o 2FA da conta está ativo
public record DeleteAccountRequest(
        @NotBlank String password,
        String totpCode
) {
}
