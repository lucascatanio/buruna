package com.buruna.engagement.web;

import java.math.BigDecimal;
import java.util.UUID;

public record RatingResponse(
        UUID mangaId,
        int score,
        BigDecimal avgRating,
        int ratingCount
) {
}
