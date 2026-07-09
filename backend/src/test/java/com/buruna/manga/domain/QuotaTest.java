package com.buruna.manga.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes puros do VO {@link Quota} — sem Spring (ADR-38). 1 GiB = 1024³ bytes.
 */
class QuotaTest {

    private static final long ONE_GB = 1024L * 1024L * 1024L;

    @Test
    void canFit_whenWithinLimit_returnsTrue() {
        Quota quota = Quota.of(BigDecimal.ONE, 100L);

        assertThat(quota.canFit(1024L)).isTrue();
    }

    @Test
    void canFit_whenExactlyAtLimit_returnsTrue() {
        Quota quota = Quota.of(BigDecimal.ONE, ONE_GB - 1024L);

        assertThat(quota.canFit(1024L)).isTrue();
    }

    @Test
    void canFit_whenExceedingLimit_returnsFalse() {
        Quota quota = Quota.of(BigDecimal.ONE, ONE_GB - 1024L);

        assertThat(quota.canFit(1024L + 1)).isFalse();
    }

    @Test
    void canFit_whenAdditionalAloneExceeds_returnsFalse() {
        Quota quota = Quota.of(BigDecimal.ONE, 0L);

        assertThat(quota.canFit(2L * ONE_GB)).isFalse();
    }

    @Test
    void remaining_returnsLimitMinusUsed() {
        Quota quota = Quota.of(BigDecimal.ONE, ONE_GB / 4);

        assertThat(quota.remaining()).isEqualTo(ONE_GB - ONE_GB / 4);
    }

    @Test
    void remaining_whenOverQuota_isNegative() {
        Quota quota = Quota.of(BigDecimal.ONE, ONE_GB + 500L);

        assertThat(quota.remaining()).isEqualTo(-500L);
    }

    @Test
    void of_convertsGigabytesToBytes() {
        Quota quota = Quota.of(BigDecimal.valueOf(2), 0L);

        assertThat(quota.limitBytes()).isEqualTo(2L * ONE_GB);
        assertThat(quota.usedBytes()).isZero();
    }
}
