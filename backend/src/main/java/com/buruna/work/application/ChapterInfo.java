package com.buruna.work.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Identificação curta de um capítulo, para o histórico de leitura. */
public record ChapterInfo(UUID chapterId, UUID workId, String language, BigDecimal number, String label) {
}
