package com.buruna.work.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterNumberTest {

    @Test
    void shouldAcceptDecimal_whenChapterIsBetweenTwoOthers() {
        assertThat(ChapterNumber.of(new BigDecimal("10.5")).toString()).isEqualTo("10.5");
    }

    @Test
    void shouldBeEqual_whenTrailingZerosDiffer() {
        assertThat(ChapterNumber.of(new BigDecimal("10.0"))).isEqualTo(ChapterNumber.of(BigDecimal.TEN));
    }

    @Test
    void shouldPrintPlainNumber_whenValueIsRoundTen() {
        assertThat(ChapterNumber.of(new BigDecimal("10.00")).toString()).isEqualTo("10");
    }

    @Test
    void shouldAcceptZero_whenChapterIsPrologue() {
        assertThat(ChapterNumber.of(BigDecimal.ZERO).value()).isEqualByComparingTo("0");
    }

    @Test
    void shouldThrowInvalidChapterNumber_whenNegative() {
        assertThatThrownBy(() -> ChapterNumber.of(new BigDecimal("-1")))
                .isInstanceOf(InvalidChapterNumberException.class);
    }

    @Test
    void shouldThrowInvalidChapterNumber_whenMoreThanTwoDecimals() {
        assertThatThrownBy(() -> ChapterNumber.of(new BigDecimal("1.255")))
                .isInstanceOf(InvalidChapterNumberException.class);
    }

    @Test
    void shouldThrowInvalidChapterNumber_whenNull() {
        assertThatThrownBy(() -> ChapterNumber.of(null)).isInstanceOf(InvalidChapterNumberException.class);
    }
}
