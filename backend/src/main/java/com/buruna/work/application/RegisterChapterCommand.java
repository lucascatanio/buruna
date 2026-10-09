package com.buruna.work.application;

import com.buruna.work.domain.ChapterKind;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pedido de registro de um capítulo. {@code number} é opcional (extra, oneshot), mas aí o
 * {@code label} é obrigatório. A posse da obra é checada por quem chama: o upload do dono ou
 * a importação de uma fonte externa.
 */
public record RegisterChapterCommand(
        UUID workId,
        String language,
        BigDecimal number,
        String label,
        String title,
        String scanlationGroup,
        ChapterKind kind,
        UUID uploadedById
) {
}
