package com.buruna.work.domain;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Objects;

/**
 * Idioma de um capítulo, como tag BCP 47 normalizada ({@code pt-br} → {@code pt-BR}). Códigos
 * próprios de uma fonte externa (ex.: {@code es-la} do MangaDex) são traduzidos por quem
 * integra a fonte, não aqui.
 */
public final class Language {

    private static final int MAX_LENGTH = 35;

    private final String value;

    private Language(String value) {
        this.value = value;
    }

    public static Language of(String tag) {
        if (tag == null || tag.isBlank() || tag.length() > MAX_LENGTH) {
            throw new InvalidLanguageException(tag);
        }
        String normalized;
        try {
            normalized = new Locale.Builder().setLanguageTag(tag.trim()).build().toLanguageTag();
        } catch (IllformedLocaleException e) {
            throw new InvalidLanguageException(tag);
        }
        if (normalized.equals("und")) {
            throw new InvalidLanguageException(tag);
        }
        return new Language(normalized);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Language that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
