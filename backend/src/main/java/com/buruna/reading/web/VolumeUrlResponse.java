package com.buruna.reading.web;

import java.util.UUID;

public record VolumeUrlResponse(
        UUID volumeId,
        String url,
        int expiresInSeconds
) {}
