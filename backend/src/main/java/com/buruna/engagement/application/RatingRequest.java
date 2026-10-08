package com.buruna.engagement.application;

import jakarta.validation.constraints.NotNull;

public record RatingRequest(
        @NotNull(message = "Score é obrigatório")
        Integer score
) {
}
