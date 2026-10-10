package com.buruna.work.application;

import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterNumber;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Item da lista de capítulos de uma obra, sem as páginas (que só o leitor carrega).
 * {@code status} e {@code failureReason} só importam para quem enviou: a lista de quem lê só
 * tem capítulos publicados.
 */
public record ChapterListItem(
        UUID id,
        String language,
        BigDecimal number,
        String label,
        String title,
        String scanlationGroup,
        String status,
        String failureReason,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt
) {

    static ChapterListItem from(Chapter chapter) {
        return new ChapterListItem(
                chapter.getId(),
                chapter.getLanguage().value(),
                chapter.getNumber().map(ChapterNumber::value).orElse(null),
                chapter.getLabel().orElse(null),
                chapter.getTitle().orElse(null),
                chapter.getScanlationGroup().orElse(null),
                chapter.getStatus().name(),
                chapter.getFailureReason().orElse(null),
                chapter.getPublishedAt().orElse(null),
                chapter.getCreatedAt());
    }
}
