package com.buruna.manga.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileHashTest {

    @Test
    void of_keepsValue() {
        assertThat(FileHash.of("d41d8cd98f00b204").value()).isEqualTo("d41d8cd98f00b204");
    }

    @Test
    void of_blankOrNull_throws() {
        assertThatThrownBy(() -> FileHash.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileHash.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equality_byValue() {
        assertThat(FileHash.of("abc")).isEqualTo(FileHash.of("abc"));
    }
}
