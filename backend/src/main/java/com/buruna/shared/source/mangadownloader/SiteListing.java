package com.buruna.shared.source.mangadownloader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * O que {@code manga-downloader list --json} devolve (ADR-54). {@code languages} e
 * {@code scanlators} são nulos quando o site não lista esse filtro.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SiteListing(
        String title,
        String grabber,
        List<Chapter> chapters,
        Filter languages,
        Filter scanlators,
        String volumes
) {

    /**
     * Uma versão de capítulo no site. {@code id} vem vazio nos sites sem id de capítulo;
     * {@code language}, {@code group} e {@code volume} vêm vazios quando o site não diz.
     * {@code groups} traz os nomes separados, cada um um valor que o {@code --scanlator} aceita
     * ({@code group} é a junção para exibir, e um nome pode conter vírgula).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chapter(
            String id,
            double number,
            boolean unnumbered,
            String title,
            String language,
            String group,
            List<String> groups,
            int volume,
            boolean locked
    ) {
    }

    /** Valores que o filtro aceita e se o site também aceita {@code all}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Filter(List<String> values, boolean all) {
    }
}
