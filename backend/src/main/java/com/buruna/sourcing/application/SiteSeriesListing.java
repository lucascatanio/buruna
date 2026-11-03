package com.buruna.sourcing.application;

import java.util.List;

/**
 * A série de um site como a tela de import a mostra (ADR-54). {@code tracked}: a série já é
 * acompanhada. Idioma vazio é o do site que não informa idioma; o ADM diz qual é.
 */
public record SiteSeriesListing(
        String title,
        String grabber,
        boolean tracked,
        List<Language> languages,
        List<Group> groups,
        List<Chapter> chapters
) {

    /** Código do idioma como o site o escreve e quantas versões ele tem. */
    public record Language(String code, int chapterCount) {
    }

    /** {@code id} é o que vai como grupo preferido no import. */
    public record Group(String id, String name, int chapterCount) {
    }

    /** {@code number} nulo: capítulo sem número, que o manga-downloader não baixa. */
    public record Chapter(String number, String title, String language, List<String> groups, Integer volume,
                          boolean locked) {
    }
}
