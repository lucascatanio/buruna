package com.buruna.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpCodeRequest(
        @NotBlank String code
) {
}
