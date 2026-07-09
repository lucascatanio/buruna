package com.buruna.engagement.domain;

/**
 * Value Object imutável representando a nota de um Rating (1–5, inclusive).
 * Invariante testável em JUnit puro, sem Spring (ADR-34, ADR-38).
 */
public final class Score {

    private final int value;

    private Score(int value) {
        if (value < 1 || value > 5) {
            throw new ScoreOutOfRangeException(value);
        }
        this.value = value;
    }

    public static Score of(int value) {
        return new Score(value);
    }

    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Score s)) return false;
        return value == s.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return "Score(" + value + ")";
    }
}
