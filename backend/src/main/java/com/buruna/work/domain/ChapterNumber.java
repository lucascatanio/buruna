package com.buruna.work.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Número de um capítulo. Aceita decimais porque fontes publicam capítulos como 10.5 entre o
 * 10 e o 11. Capítulo sem número (extra, oneshot) não tem este VO: usa só o rótulo.
 */
public final class ChapterNumber implements Comparable<ChapterNumber> {

    private static final int MAX_SCALE = 2;

    private final BigDecimal value;

    private ChapterNumber(BigDecimal value) {
        this.value = value;
    }

    public static ChapterNumber of(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new InvalidChapterNumberException(value);
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > MAX_SCALE) {
            throw new InvalidChapterNumberException(value);
        }
        // 10 e 10.0 são o mesmo capítulo; sem escala negativa (1E+1) no toString
        return new ChapterNumber(normalized.scale() < 0 ? normalized.setScale(0) : normalized);
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public int compareTo(ChapterNumber other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChapterNumber that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
