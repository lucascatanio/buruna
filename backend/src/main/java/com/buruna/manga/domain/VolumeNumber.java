package com.buruna.manga.domain;

import java.util.Objects;

/**
 * Número de um volume dentro de um mangá. VO de borda: a entidade {@link Volume}
 * persiste um {@code int}; este VO garante o invariante {@code >= 1}.
 */
public final class VolumeNumber {

    private final int value;

    private VolumeNumber(int value) {
        this.value = value;
    }

    public static VolumeNumber of(int value) {
        if (value < 1) {
            throw new InvalidVolumeNumberException(value);
        }
        return new VolumeNumber(value);
    }

    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VolumeNumber that)) return false;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
