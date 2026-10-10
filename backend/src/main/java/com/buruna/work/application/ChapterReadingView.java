package com.buruna.work.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O que o contexto {@code reading} precisa para abrir um capítulo: as páginas (nomes no storage
 * e dimensões) e os vizinhos no mesmo idioma, para o leitor pré-carregar o próximo; ou, num
 * livro, o arquivo.
 */
public record ChapterReadingView(
        UUID chapterId,
        UUID workId,
        String language,
        BigDecimal number,
        String label,
        String title,
        String scanlationGroup,
        String kind,
        List<Page> pages,
        /** Só num livro: o arquivo inteiro (PDF ou EPUB). */
        File file,
        UUID previousChapterId,
        UUID nextChapterId
) {

    public record Page(String objectName, String dataSaverObjectName, int width, int height) {
    }

    public record File(String objectName, String format, Integer pageCount) {
    }
}
