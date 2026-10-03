package com.buruna.reading.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code totalPages} é opcional: clientes antigos só mandam a página atual. */
public record ProgressRequest(
        @NotNull @Min(1) Integer currentPage,
        @Min(1) Integer totalPages
) {}
