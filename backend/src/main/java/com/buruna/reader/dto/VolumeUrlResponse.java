package com.buruna.reader.dto;

import java.util.UUID;

public record VolumeUrlResponse(
        UUID volumeId,
        String url,
        int expiresInSeconds
) {
}