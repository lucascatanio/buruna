package com.buruna.shared.source;

import java.util.List;

/**
 * Base de metadados de obras (ADR-54): busca, detalhes e capa, sem capítulos. Hoje o AniList.
 * As tags saem no vocabulário em inglês que {@link ExternalSource} já usa, para obras de bases
 * diferentes caírem nas mesmas tags do catálogo.
 *
 * <p>Falha de rede ou resposta inesperada vira {@link ExternalSourceException}.
 */
public interface WorkMetadataSource {

    /** Busca por título, para o ADM escolher a obra. */
    List<SourceWorkSummary> search(String title, int limit);

    /** Metadados completos de uma obra. Obra inexistente → {@link ExternalWorkNotFoundException}. */
    SourceWorkDetails fetchWork(String externalId);

    /** Baixa uma capa devolvida por esta base, com no máximo {@code maxBytes}. Quem chama confere o tipo. */
    byte[] download(String url, long maxBytes);
}
