package com.buruna.work.application;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Idioma e número do capítulo que vai ser enviado. O número é conferido já aqui para o
 * usuário não subir um arquivo grande no 4G e só descobrir no fim que o capítulo existe.
 */
public record ChapterUploadUrlRequest(
        @NotBlank String language,
        BigDecimal number,
        /** Extensão do arquivo: cbz, cbr, pdf ou epub. Ausente vale cbz, como antes do upload de outros formatos. */
        String format
) {}
