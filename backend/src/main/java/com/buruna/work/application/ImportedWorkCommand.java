package com.buruna.work.application;

import java.util.List;

/**
 * Metadados de uma obra vinda de fonte externa, em tipos simples: quem importa não conhece os
 * enums do domínio. {@code format} e {@code statusOrigin} são nomes de {@code WorkFormat} e
 * {@code WorkStatusOrigin}; {@code coverDataUri} é a capa como data URI, ou nulo; {@code aniListId}
 * é a obra no AniList de onde vieram os metadados (ADR-54), ou nulo.
 */
public record ImportedWorkCommand(
        String title,
        List<String> alternativeTitles,
        String synopsis,
        String coverDataUri,
        String format,
        String originCountry,
        String statusOrigin,
        Integer year,
        List<String> contentWarnings,
        List<TagSpec> tags,
        Integer aniListId
) {
}
