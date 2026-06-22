package com.buruna.identity.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuotaTest {

    private static final long ONE_GB = 1024L * 1024L * 1024L;

    @Test
    void of_positive_keepsValue() {
        assertThat(Quota.of(new BigDecimal("2.00")).gigabytes()).isEqualByComparingTo("2.00");
    }

    @Test
    void of_zero_throwsValidation() {
        assertThatThrownBy(() -> Quota.of(BigDecimal.ZERO))
                .isInstanceOf(InvalidQuotaException.class);
    }

    @Test
    void of_negative_throwsValidation() {
        assertThatThrownBy(() -> Quota.of(new BigDecimal("-1")))
                .isInstanceOf(InvalidQuotaException.class);
    }

    @Test
    void of_null_throwsValidation() {
        assertThatThrownBy(() -> Quota.of(null))
                .isInstanceOf(InvalidQuotaException.class);
    }

    @Test
    void bytes_convertsGigabytesToBytes() {
        assertThat(Quota.of(BigDecimal.ONE).bytes()).isEqualTo(ONE_GB);
        assertThat(Quota.of(new BigDecimal("2")).bytes()).isEqualTo(2 * ONE_GB);
    }

    @Test
    void canFit_withinQuota_isTrue() {
        Quota quota = Quota.of(BigDecimal.ONE);
        assertThat(quota.canFit(0, ONE_GB)).isTrue();
        assertThat(quota.canFit(ONE_GB / 2, ONE_GB / 2)).isTrue();
    }

    @Test
    void canFit_exceedingQuota_isFalse() {
        Quota quota = Quota.of(BigDecimal.ONE);
        assertThat(quota.canFit(ONE_GB, 1)).isFalse();
    }

    @Test
    void remaining_returnsDifference_evenWhenNegative() {
        Quota quota = Quota.of(BigDecimal.ONE);
        assertThat(quota.remaining(0)).isEqualTo(ONE_GB);
        assertThat(quota.remaining(ONE_GB + 10)).isEqualTo(-10);
    }
}
