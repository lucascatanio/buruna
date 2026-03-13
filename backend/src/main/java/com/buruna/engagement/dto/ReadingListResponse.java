package com.buruna.engagement.dto;

import com.buruna.engagement.domain.ReadingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadingListResponse(
        UUID mangaId,
        String mangaSlug,
        String mangaTitle,
        String mangaCoverUrl,
        ReadingStatus status,
        OffsetDateTime updatedAt
) {
}