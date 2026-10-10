package com.buruna.work.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O que o contexto {@code reading} precisa para abrir um capítulo: as páginas (nomes no storage
 * e dimensões) e os vizinhos no mesmo idioma, para o leitor pré-carregar o próximo.
 */
public record ChapterReadingView(
        UUID chapterId,
        UUID workId,
        String language,
        BigDecimal number,
        String label,
        String title,
        String scanlationGroup,
        List<Page> pages,
        UUID previousChapterId,
        UUID nextChapterId
) {

    public record Page(String objectName, String dataSaverObjectName, int width, int height) {
    }
}
