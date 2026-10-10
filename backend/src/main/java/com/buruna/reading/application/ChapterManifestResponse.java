package com.buruna.reading.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tudo que o leitor precisa para um capítulo de imagens: uma URL assinada por página, com as
 * dimensões para reservar espaço antes de a imagem chegar, e os vizinhos no mesmo idioma.
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
        List<Page> pages,
        UUID previousChapterId,
        UUID nextChapterId,
        Instant urlsExpireAt
) {

    public record Page(String url, String dataSaverUrl, int width, int height) {
    }
}
