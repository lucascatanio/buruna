package com.buruna.shared.source;

import java.util.List;
import java.util.Set;

/**
 * Fonte externa de obras e capítulos (ADR-46). Uma implementação por fonte; hoje só o MangaDex.
 * Idiomas entram e saem como tag BCP 47 ({@code pt-BR}, {@code es-419}): a tradução dos códigos
 * próprios da fonte fica na implementação.
 *
 * <p>As implementações respeitam os limites de taxa da fonte por conta própria. Falha de rede
 * ou resposta inesperada vira {@link ExternalSourceException}.
 */
public interface ExternalSource {

    /** Busca por título, para o ADM escolher a obra. */
    List<SourceWorkSummary> search(String title, int limit);

    /** Metadados completos de uma obra. Obra inexistente → {@link ExternalWorkNotFoundException}. */
    SourceWorkDetails fetchWork(String externalId);

    /**
     * Todas as versões de capítulo publicadas nos idiomas pedidos, de todos os grupos, sem os
     * capítulos que só apontam para fora (sem páginas na fonte).
     */
    List<SourceChapterInfo> listChapters(String externalId, Set<String> languages);

    /** Endereços das páginas de um capítulo, na ordem. Valem por poucos minutos. */
    List<SourcePage> fetchPages(String externalChapterId);

    /**
     * Baixa uma imagem (página ou capa) devolvida por esta fonte, com no máximo {@code maxBytes}.
     * Quem chama confere o tipo e as dimensões.
     */
    byte[] download(String url, long maxBytes);
}
