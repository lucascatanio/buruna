package com.buruna.sourcing.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SourceChapterImporterTest {

    @Test
    void shouldParseDecimal_whenNumberFitsTheColumn() {
        assertThat(SourceChapterImporter.parseNumber("10.5")).isEqualByComparingTo(new BigDecimal("10.5"));
        assertThat(SourceChapterImporter.parseNumber("100")).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void shouldReturnNull_whenNumberDoesNotFit() {
        assertThat(SourceChapterImporter.parseNumber("10.125")).isNull();
        assertThat(SourceChapterImporter.parseNumber("10a")).isNull();
        assertThat(SourceChapterImporter.parseNumber("-1")).isNull();
    }
}
