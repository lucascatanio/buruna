package com.buruna.reader.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProgressResponse(
        UUID volumeId,
        int currentPage,
        OffsetDateTime updatedAt
) {
}