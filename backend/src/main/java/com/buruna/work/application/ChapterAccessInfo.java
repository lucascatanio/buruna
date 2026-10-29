package com.buruna.work.application;

import java.util.UUID;

/**
 * {@code pageCount} é o total de páginas de um capítulo de imagens ou de um livro em PDF; num EPUB,
 * que não tem página fixa, é nulo e o progresso vai por posição.
 */
public record ChapterAccessInfo(UUID chapterId, UUID workId, Integer pageCount, boolean epub) {
}
