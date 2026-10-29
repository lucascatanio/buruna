package com.buruna.work.domain;

/**
 * {@code PROCESSING}: registrado, com o conteúdo ainda sendo extraído ou baixado.
 * {@code PUBLISHED}: visível para leitura. {@code FAILED}: o processamento não terminou; o
 * número fica livre para uma nova tentativa. {@code UNPUBLISHED}: tirado do ar, mantendo os
 * dados.
 */
public enum ChapterStatus {
    PROCESSING,
    PUBLISHED,
    FAILED,
    UNPUBLISHED
}
