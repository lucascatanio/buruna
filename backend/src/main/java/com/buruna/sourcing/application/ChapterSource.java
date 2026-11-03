package com.buruna.sourcing.application;

import com.buruna.shared.source.SourceChapterInfo;
import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.TrackedWork;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * O que muda de uma fonte para outra no executor (ADR-54): listar as versões de capítulo e baixar
 * as páginas de uma delas. Escolha de versão, registro, publicação e troca de versão são comuns.
 */
interface ChapterSource {

    SourceName source();

    /**
     * Versões publicadas nos idiomas acompanhados, sem as bloqueadas, com o idioma como a fonte o
     * escreve ({@link TrackedWork#languageFromSource} traduz).
     */
    List<SourceChapterInfo> listChapters(TrackedWork tracked);

    /** Idiomas em que a fonte tem a obra, como ela os escreve. */
    Set<String> availableLanguages(TrackedWork tracked);

    /**
     * Entrega as páginas da versão ao {@code sink}, uma por vez e na ordem. Versão sem página →
     * {@link com.buruna.shared.source.ExternalSourceException}.
     */
    void fetchPages(TrackedWork tracked, SourceChapter version, Consumer<FetchedPage> sink);
}
