package com.buruna.shared.source;

import java.util.List;

/**
 * Resultado de busca. {@code coverThumbnailUrl} é da fonte e só abre pelo backend (a fonte
 * recusa hotlink); nulo quando a obra não tem capa.
 */
public record SourceWorkSummary(
        String externalId,
        String title,
        Integer year,
        SourceContentRating contentRating,
        String originalLanguage,
        SourceWorkStatus status,
        List<String> availableLanguages,
        String coverThumbnailUrl,
        String sourceUrl
) {
}
