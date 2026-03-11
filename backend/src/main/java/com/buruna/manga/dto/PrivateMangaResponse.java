package com.buruna.manga.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PrivateMangaResponse(
        UUID id,
        String title,
        String synopsis,
        String coverUrl,
        List<VolumeResponse> volumes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}