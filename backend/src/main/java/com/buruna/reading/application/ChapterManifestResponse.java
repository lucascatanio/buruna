package com.buruna.reading.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tudo que o leitor precisa para um capítulo de imagens: uma URL assinada por página, com as
 * dimensões para reservar espaço antes de a imagem chegar, e os vizinhos no mesmo idioma. Num
 * livro, uma URL assinada para o arquivo inteiro, e o PDF é lido por range requests.
 * {@code dataSaverUrl} só existe quando a fonte fornece a variante de economia de dados.
 */
public record ChapterManifestResponse(
        UUID chapterId,
        UUID workId,
        String language,
        BigDecimal number,
        String label,
        String title,
        String scanlationGroup,
        /** PAGES (imagens) ou FILE (livro, em {@code file}). */
        String kind,
        List<Page> pages,
        File file,
        UUID previousChapterId,
        UUID nextChapterId,
        Instant urlsExpireAt
) {

    public record Page(String url, String dataSaverUrl, int width, int height) {
    }

    /** Livro: o arquivo inteiro. {@code pageCount} só no PDF. */
    public record File(String url, String format, Integer pageCount) {
    }
}
