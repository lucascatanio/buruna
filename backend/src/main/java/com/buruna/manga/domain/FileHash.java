package com.buruna.manga.domain;

import java.util.Objects;

/**
 * Hash do arquivo de um volume (md5 do objeto no storage). VO de borda: a entidade
 * {@link Volume} persiste a string; este VO garante que o hash não é vazio.
 */
public final class FileHash {

    private final String value;

    private FileHash(String value) {
        this.value = value;
    }

    public static FileHash of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FileHash não pode ser vazio");
        }
        return new FileHash(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileHash fileHash)) return false;
        return value.equals(fileHash.value);
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
