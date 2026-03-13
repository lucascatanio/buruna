package com.buruna.engagement.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RatingResponse(
        UUID mangaId,
        int score,
        BigDecimal avgRating,
        int ratingCount
) {
}