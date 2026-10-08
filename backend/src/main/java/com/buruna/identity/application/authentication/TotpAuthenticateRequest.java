package com.buruna.identity.application.authentication;

import jakarta.validation.constraints.NotBlank;

public record TotpAuthenticateRequest(
        @NotBlank String tempToken,
        @NotBlank String totpCode
) {
}
