package com.buruna.work.application;

import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterNumber;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ChapterResponse(
        UUID id,
        UUID workId,
        String language,
        BigDecimal number,
        String label,
        String title,
        String scanlationGroup,
        String kind,
        String status,
        int pageCount,
        OffsetDateTime publishedAt
) {

    static ChapterResponse from(Chapter chapter) {
        return new ChapterResponse(
                chapter.getId(),
                chapter.getWorkId(),
                chapter.getLanguage().value(),
                chapter.getNumber().map(ChapterNumber::value).orElse(null),
                chapter.getLabel().orElse(null),
                chapter.getTitle().orElse(null),
                chapter.getScanlationGroup().orElse(null),
                chapter.getKind().name(),
                chapter.getStatus().name(),
                chapter.getFilePageCount().orElse(chapter.getPages().size()),
                chapter.getPublishedAt().orElse(null)
        );
    }
}
