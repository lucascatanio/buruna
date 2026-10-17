package com.buruna.shared.source.mangadex;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MangaDexLanguagesTest {

    @Test
    void shouldUppercaseRegion_whenConvertingBrazilianPortugueseToBcp47() {
        assertThat(MangaDexLanguages.toBcp47("pt-br")).isEqualTo("pt-BR");
    }

    @Test
    void shouldConvertToLatinAmericaRegion_whenSpanishIsLatinAmerican() {
        assertThat(MangaDexLanguages.toBcp47("es-la")).isEqualTo("es-419");
    }

    @Test
    void shouldUppercaseRegion_whenConvertingHongKongChineseToBcp47() {
        assertThat(MangaDexLanguages.toBcp47("zh-hk")).isEqualTo("zh-HK");
    }

    @Test
    void shouldUseLatinScript_whenCodeIsRomanized() {
        assertThat(MangaDexLanguages.toBcp47("ja-ro")).isEqualTo("ja-Latn");
        assertThat(MangaDexLanguages.toBcp47("ko-ro")).isEqualTo("ko-Latn");
    }

    @Test
    void shouldKeepCode_whenItHasTwoLetters() {
        assertThat(MangaDexLanguages.toBcp47("en")).isEqualTo("en");
        assertThat(MangaDexLanguages.toBcp47("ja")).isEqualTo("ja");
    }

    @Test
    void shouldLowercaseRegion_whenConvertingBackToMangaDex() {
        assertThat(MangaDexLanguages.toMangaDex("pt-BR")).isEqualTo("pt-br");
        assertThat(MangaDexLanguages.toMangaDex("zh-HK")).isEqualTo("zh-hk");
    }

    @Test
    void shouldConvertToLatinAmericanSpanish_whenTagIsEs419() {
        assertThat(MangaDexLanguages.toMangaDex("es-419")).isEqualTo("es-la");
    }

    @Test
    void shouldUseRomanizedSuffix_whenTagHasLatinScript() {
        assertThat(MangaDexLanguages.toMangaDex("ja-Latn")).isEqualTo("ja-ro");
    }

    @Test
    void shouldKeepCode_whenConvertingBackATwoLetterTag() {
        assertThat(MangaDexLanguages.toMangaDex("en")).isEqualTo("en");
    }
}
