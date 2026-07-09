package com.buruna.identity.domain;

import java.math.BigDecimal;

/**
 * Value Object imutável de cota de armazenamento, expresso em gigabytes.
 * Valida na construção (deve ser positiva) e concentra a aritmética de cota
 * ({@link #canFit}/{@link #remaining}) em bytes, consistente com o cálculo de
 * {@code StorageQuotaService} (1 GiB = 1024³ bytes). A entidade persiste o
 * {@link BigDecimal} em GB; o VO é usado na borda da application.
 */
public final class Quota {

    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;

    private final BigDecimal gigabytes;

    private Quota(BigDecimal gigabytes) {
        if (gigabytes == null || gigabytes.signum() <= 0) {
            throw new InvalidQuotaException(gigabytes);
        }
        this.gigabytes = gigabytes;
    }

    public static Quota of(BigDecimal gigabytes) {
        return new Quota(gigabytes);
    }

    public BigDecimal gigabytes() {
        return gigabytes;
    }

    public long bytes() {
        return gigabytes.multiply(BigDecimal.valueOf(BYTES_PER_GB)).longValue();
    }

    /** Verdadeiro se {@code usedBytes + additionalBytes} cabe dentro da cota. */
    public boolean canFit(long usedBytes, long additionalBytes) {
        return usedBytes + additionalBytes <= bytes();
    }

    /** Bytes restantes dado o consumo atual (pode ser negativo se estourado). */
    public long remaining(long usedBytes) {
        return bytes() - usedBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quota q)) return false;
        return gigabytes.compareTo(q.gigabytes) == 0;
    }

    @Override
    public int hashCode() {
        return gigabytes.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return gigabytes + " GB";
    }
}
