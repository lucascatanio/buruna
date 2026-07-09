package com.buruna.manga.domain;

import java.math.BigDecimal;

/**
 * Value Object imutável da cota de armazenamento da coleção privada (ADR-34 §4.3).
 * Concentra a aritmética de cota ({@link #canFit}/{@link #remaining}) em bytes
 * (1 GiB = 1024³). O limite chega como {@link BigDecimal} em GB (atributo do usuário,
 * passado como primitivo na borda — ADR-35); o consumo é somado das tabelas do próprio
 * contexto manga. Puro: testável em JUnit sem Spring.
 */
public final class Quota {

    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;

    private final long limitBytes;
    private final long usedBytes;

    private Quota(long limitBytes, long usedBytes) {
        this.limitBytes = limitBytes;
        this.usedBytes = usedBytes;
    }

    /** Cria a cota a partir do limite em GB e do consumo atual em bytes. */
    public static Quota of(BigDecimal limitGb, long usedBytes) {
        long limitBytes = limitGb.multiply(BigDecimal.valueOf(BYTES_PER_GB)).longValue();
        return new Quota(limitBytes, usedBytes);
    }

    /** Verdadeiro se {@code usedBytes + additionalBytes} cabe dentro do limite. */
    public boolean canFit(long additionalBytes) {
        return usedBytes + additionalBytes <= limitBytes;
    }

    /** Bytes restantes dado o consumo atual (pode ser negativo se já estourado). */
    public long remaining() {
        return limitBytes - usedBytes;
    }

    public long usedBytes() {
        return usedBytes;
    }

    public long limitBytes() {
        return limitBytes;
    }
}
