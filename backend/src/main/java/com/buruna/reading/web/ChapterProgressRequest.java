package com.buruna.reading.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Só a página atual: o total vem do capítulo, no servidor. */
public record ChapterProgressRequest(
        @NotNull @Min(1) Integer currentPage
) {}
