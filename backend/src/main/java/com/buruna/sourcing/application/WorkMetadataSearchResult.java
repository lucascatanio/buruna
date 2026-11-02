package com.buruna.sourcing.application;

import com.buruna.shared.source.SourceWorkSummary;

/** Resultado da busca na base de metadados. {@code coverThumbnailUrl} só abre pelo backend. */
public record WorkMetadataSearchResult(
        String externalId,
        String title,
        Integer year,
        String contentRating,
        String originalLanguage,
        String status,
        String coverThumbnailUrl,
        String sourceUrl
) {
    static WorkMetadataSearchResult from(SourceWorkSummary summary) {
        return new WorkMetadataSearchResult(summary.externalId(), summary.title(), summary.year(),
                summary.contentRating() == null ? null : summary.contentRating().name(),
                summary.originalLanguage(),
                summary.status() == null ? null : summary.status().name(),
                summary.coverThumbnailUrl(), summary.sourceUrl());
    }
}
