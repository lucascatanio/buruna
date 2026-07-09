package com.buruna.engagement.web;

import jakarta.validation.constraints.NotNull;

public record RatingRequest(
        @NotNull(message = "Score é obrigatório")
        Integer score
) {
}
