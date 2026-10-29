package com.buruna.reading.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Capítulo de páginas (e livro em PDF): só a página atual, porque o total vem do servidor. EPUB:
 * a posição (CFI) e o andamento de 0 a 1.
 */
public record ChapterProgressRequest(
        @Min(1) Integer currentPage,
        @Size(max = 2000) String position,
        @DecimalMin("0") @DecimalMax("1") BigDecimal percent
) {}
