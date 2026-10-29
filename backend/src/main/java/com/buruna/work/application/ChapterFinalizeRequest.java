package com.buruna.work.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ChapterFinalizeRequest(
        @NotBlank String objectName,
        @NotBlank String language,
        BigDecimal number,
        @Size(max = 100) String label,
        @Size(max = 255) String title,
        @Size(max = 255) String scanlationGroup
) {}
