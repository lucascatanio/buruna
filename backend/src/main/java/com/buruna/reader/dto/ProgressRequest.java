package com.buruna.reader.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProgressRequest(
        @NotNull @Min(1) Integer currentPage
) {
}