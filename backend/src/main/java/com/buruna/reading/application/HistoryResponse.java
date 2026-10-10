package com.buruna.reading.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entrada do histórico: de um volume (legado: {@code volumeId}, {@code volumeNumber}) ou de um
 * capítulo ({@code chapterId}, {@code chapterNumber}/{@code chapterLabel}, {@code language}).
 */
public record HistoryResponse(
        UUID volumeId,
        Integer volumeNumber,
        UUID chapterId,
        BigDecimal chapterNumber,
        String chapterLabel,
        String language,
        UUID workId,
        String workTitle,
        String workCoverUrl,
        OffsetDateTime readAt
) {}
