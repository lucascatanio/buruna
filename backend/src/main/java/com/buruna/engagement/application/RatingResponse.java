package com.buruna.engagement.application;

import java.math.BigDecimal;
import java.util.UUID;

public record RatingResponse(
        UUID workId,
        int score,
        BigDecimal avgRating,
        int ratingCount
) {
}
