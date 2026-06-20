package com.buruna.reading.web;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoryResponse(
        UUID volumeId,
        int volumeNumber,
        UUID mangaId,
        String mangaTitle,
        String mangaCoverUrl,
        OffsetDateTime readAt
) {}
