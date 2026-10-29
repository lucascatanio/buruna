package com.buruna.work.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageTest {

    @Test
    void shouldNormalizeCase_whenTagHasRegion() {
        // Act
        Language language = Language.of("pt-br");

        // Assert
        assertThat(language.value()).isEqualTo("pt-BR");
    }

    @Test
    void shouldKeepNumericRegion_whenTagIsLatinAmericanSpanish() {
        assertThat(Language.of("es-419").value()).isEqualTo("es-419");
    }

    @Test
    void shouldBeEqual_whenTagsDifferOnlyInCase() {
        assertThat(Language.of("EN")).isEqualTo(Language.of("en"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "pt_BR", "português", "und"})
    void shouldThrowInvalidLanguage_whenTagIsNotBcp47(String tag) {
        assertThatThrownBy(() -> Language.of(tag)).isInstanceOf(InvalidLanguageException.class);
    }
}
