package com.buruna.reading.application;

import java.util.UUID;

public record VolumeUrlResponse(
        UUID volumeId,
        String url,
        int expiresInSeconds
) {}
