package com.buruna.identity.web;

import jakarta.validation.constraints.NotBlank;

public record TotpCodeRequest(
        @NotBlank String code
) {
}
