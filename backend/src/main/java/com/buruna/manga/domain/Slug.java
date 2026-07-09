package com.buruna.manga.domain;

import java.text.Normalizer;
import java.util.Objects;

/**
 * Slug de URL de um mangá. VO de borda (ADR-32/34): a entidade persiste a string crua
 * na coluna {@code slug}; este VO centraliza a normalização (antes duplicada nos services).
 * A unicidade depende do repositório e permanece na camada application ({@code SlugAllocator})
 * — aqui só se gera a forma base e variações com sufixo.
 */
public final class Slug {

    private final String value;

    private Slug(String value) {
        this.value = value;
    }

    public static Slug of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Slug não pode ser vazio");
        }
        return new Slug(value);
    }

    /** Normaliza um título em um slug base (sem garantia de unicidade). */
    public static Slug fromTitle(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String base = normalized.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        return new Slug(base);
    }

    /** Retorna uma nova variação com sufixo numérico (ex.: {@code naruto} → {@code naruto-2}). */
    public Slug withSuffix(int suffix) {
        return new Slug(value + "-" + suffix);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Slug slug)) return false;
        return value.equals(slug.value);
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
