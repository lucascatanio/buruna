package com.buruna.engagement.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreTest {

    @Test
    void score_boundary1_isValid() {
        assertThat(Score.of(1).value()).isEqualTo(1);
    }

    @Test
    void score_boundary5_isValid() {
        assertThat(Score.of(5).value()).isEqualTo(5);
    }

    @Test
    void score_midRange3_isValid() {
        assertThat(Score.of(3).value()).isEqualTo(3);
    }

    @Test
    void score_zero_throwsScoreOutOfRangeException() {
        assertThatThrownBy(() -> Score.of(0))
                .isInstanceOf(ScoreOutOfRangeException.class);
    }

    @Test
    void score_six_throwsScoreOutOfRangeException() {
        assertThatThrownBy(() -> Score.of(6))
                .isInstanceOf(ScoreOutOfRangeException.class);
    }

    @Test
    void score_negative_throwsScoreOutOfRangeException() {
        assertThatThrownBy(() -> Score.of(-1))
                .isInstanceOf(ScoreOutOfRangeException.class);
    }

    @Test
    void score_equality_sameValue_areEqual() {
        assertThat(Score.of(3)).isEqualTo(Score.of(3));
    }

    @Test
    void score_equality_differentValues_areNotEqual() {
        assertThat(Score.of(3)).isNotEqualTo(Score.of(4));
    }
}
