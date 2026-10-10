package com.buruna.reading.application;

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
        OffsetDateTime updatedAt
) {
    public static ProgressResponse from(ReadingProgress progress) {
        return new ProgressResponse(
                progress.getVolumeId(),
                progress.getChapterId(),
                progress.getCurrentPage(),
                progress.getTotalPages(),
                progress.isFinished(),
                progress.getUpdatedAt());
    }
}
