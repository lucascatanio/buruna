package com.buruna.reading.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoryResponse(
        UUID volumeId,
        int volumeNumber,
        UUID workId,
        String workTitle,
        String workCoverUrl,
        OffsetDateTime readAt
) {}
