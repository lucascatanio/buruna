package com.buruna.reading.application;

import java.math.BigDecimal;
import com.buruna.reading.domain.ReadingProgress;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code volumeId} (legado) ou {@code chapterId}: só um dos dois vem preenchido. */
public record ProgressResponse(
        UUID volumeId,
        UUID chapterId,
        int currentPage,
        Integer totalPages,
        boolean finished,
        /** Num EPUB: a posição (CFI) e o andamento de 0 a 1. Nulos em capítulo de páginas. */
        String position,
        BigDecimal percent,
        OffsetDateTime updatedAt
) {
    public static ProgressResponse from(ReadingProgress progress) {
        return new ProgressResponse(
                progress.getVolumeId(),
                progress.getChapterId(),
                progress.getCurrentPage(),
                progress.getTotalPages(),
                progress.isFinished(),
                progress.getPosition().orElse(null),
                progress.getPercent().orElse(null),
                progress.getUpdatedAt());
    }
}
