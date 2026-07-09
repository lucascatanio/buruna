package com.buruna.identity.web;

import jakarta.validation.constraints.NotBlank;

public record TotpAuthenticateRequest(
        @NotBlank String tempToken,
        @NotBlank String totpCode
) {
}
