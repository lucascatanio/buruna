package com.buruna.sourcing.application;

import com.buruna.shared.source.SourceWorkSummary;

import java.util.List;

/** Resultado da busca para o ADM. {@code tracked}: a obra já é acompanhada. */
public record SourceSearchResult(
        String externalId,
        String title,
        Integer year,
        String contentRating,
        String originalLanguage,
        String status,
        List<String> availableLanguages,
        String coverThumbnailUrl,
        String sourceUrl,
        boolean tracked
) {

    static SourceSearchResult from(SourceWorkSummary summary, boolean tracked) {
        return new SourceSearchResult(summary.externalId(), summary.title(), summary.year(),
                summary.contentRating() == null ? null : summary.contentRating().name(),
                summary.originalLanguage(),
                summary.status() == null ? null : summary.status().name(),
                summary.availableLanguages(), summary.coverThumbnailUrl(), summary.sourceUrl(), tracked);
    }
}
