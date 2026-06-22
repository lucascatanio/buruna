package com.buruna.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Presença (@NotBlank) é validada na borda; o formato de e-mail e o tamanho de
 * username migraram para os VOs {@code Email}/{@code Username} no domínio (ADR-34),
 * espelhando como {@code RatingRequest} delegou faixa de score a {@code Score}.
 */
public record RegisterRequest(
        @NotBlank String email,
        @NotBlank String username,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @NotBlank String presentationMessage,
        String avatarBase64,
        @NotBlank String captchaToken
) {
}
