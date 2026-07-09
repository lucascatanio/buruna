package com.buruna.manga.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolumeNumberTest {

    @Test
    void of_validNumber_keepsValue() {
        assertThat(VolumeNumber.of(1).value()).isEqualTo(1);
        assertThat(VolumeNumber.of(42).value()).isEqualTo(42);
    }

    @Test
    void of_zeroOrNegative_throws() {
        assertThatThrownBy(() -> VolumeNumber.of(0)).isInstanceOf(InvalidVolumeNumberException.class);
        assertThatThrownBy(() -> VolumeNumber.of(-1)).isInstanceOf(InvalidVolumeNumberException.class);
    }

    @Test
    void equality_byValue() {
        assertThat(VolumeNumber.of(3)).isEqualTo(VolumeNumber.of(3));
    }
}
