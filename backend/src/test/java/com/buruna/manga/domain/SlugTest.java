package com.buruna.manga.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugTest {

    @Test
    void fromTitle_normalizesToKebabCase() {
        assertThat(Slug.fromTitle("One Piece").value()).isEqualTo("one-piece");
    }

    @Test
    void fromTitle_stripsPunctuationAndAccents() {
        assertThat(Slug.fromTitle("Açaí: O Início!").value()).isEqualTo("acai-o-inicio");
    }

    @Test
    void fromTitle_collapsesWhitespaceAndDashes() {
        assertThat(Slug.fromTitle("  Naruto   Shippuden  ").value()).isEqualTo("naruto-shippuden");
    }

    @Test
    void withSuffix_appendsNumber() {
        assertThat(Slug.fromTitle("naruto").withSuffix(2).value()).isEqualTo("naruto-2");
    }

    @Test
    void of_blank_throws() {
        assertThatThrownBy(() -> Slug.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Slug.of("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
