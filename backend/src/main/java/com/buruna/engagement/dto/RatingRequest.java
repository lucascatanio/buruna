package com.buruna.engagement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RatingRequest(
        @NotNull(message = "Score é obrigatório")
        @Min(value = 1, message = "Score mínimo é 1")
        @Max(value = 5, message = "Score máximo é 5")
        Integer score
) {
}