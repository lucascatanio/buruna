package com.buruna.manga.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VolumeResponse(
        UUID id,
        Integer volumeNumber,
        Long fileSizeBytes,
        OffsetDateTime createdAt
) {
}