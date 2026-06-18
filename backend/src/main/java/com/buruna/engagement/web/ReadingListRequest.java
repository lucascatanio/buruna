package com.buruna.engagement.web;

import com.buruna.engagement.domain.ReadingStatus;
import jakarta.validation.constraints.NotNull;

public record ReadingListRequest(
        @NotNull(message = "Status é obrigatório")
        ReadingStatus status
) {
}
