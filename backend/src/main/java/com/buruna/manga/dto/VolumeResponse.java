package com.buruna.manga.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VolumeResponse(
        UUID id,
        Integer volumeNumber,
        Long fileSizeBytes,
        OffsetDateTime createdAt
) {
}