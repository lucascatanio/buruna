package com.buruna.reading.web;

import com.buruna.reading.domain.ReadingProgress;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProgressResponse(
        UUID volumeId,
        int currentPage,
        Integer totalPages,
        boolean finished,
        OffsetDateTime updatedAt
) {
    public static ProgressResponse from(ReadingProgress progress) {
        return new ProgressResponse(
                progress.getVolumeId(),
                progress.getCurrentPage(),
                progress.getTotalPages(),
                progress.isFinished(),
                progress.getUpdatedAt());
    }
}
