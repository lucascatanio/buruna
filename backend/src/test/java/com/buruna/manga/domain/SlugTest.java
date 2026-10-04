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
    void withSuffix_baseAtMaxLength_truncatesBaseToFitColumn() {
        Slug base = Slug.fromTitle("a".repeat(Slug.MAX_LENGTH));

        Slug suffixed = base.withSuffix(12);

        assertThat(suffixed.value()).hasSize(Slug.MAX_LENGTH).endsWith("a-12");
    }

    @Test
    void withSuffix_truncationEndingOnDash_doesNotDoubleTheDash() {
        // corte cai logo depois de um hífen: "...a-" + "-2" viraria "a--2"
        Slug base = Slug.fromTitle("a".repeat(Slug.MAX_LENGTH - 3) + " bb");

        assertThat(base.withSuffix(2).value()).doesNotContain("--").endsWith("a-2");
    }

    @Test
    void of_blank_throws() {
        assertThatThrownBy(() -> Slug.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Slug.of("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
