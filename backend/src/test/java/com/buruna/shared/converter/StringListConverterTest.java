package com.buruna.shared.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste unitário puro (sem Spring, sem banco) — prova que a "base rápida" da pirâmide
 * de testes (ADR-38) funciona: a classe é instanciada com `new` e exercitada direto.
 */
class StringListConverterTest {

    private final StringListConverter converter = new StringListConverter();

    @Test
    void shouldSerializeListToJson_whenListHasElements() {
        String json = converter.convertToDatabaseColumn(List.of("Attack on Titan", "Shingeki no Kyojin"));

        assertThat(json).isEqualTo("[\"Attack on Titan\",\"Shingeki no Kyojin\"]");
    }

    @Test
    void shouldReturnEmptyJsonArray_whenListIsNullOrEmpty() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
        assertThat(converter.convertToDatabaseColumn(List.of())).isEqualTo("[]");
    }

    @Test
    void shouldDeserializeJsonToList_whenJsonHasElements() {
        List<String> result = converter.convertToEntityAttribute("[\"a\",\"b\"]");

        assertThat(result).containsExactly("a", "b");
    }

    @Test
    void shouldReturnEmptyList_whenDbDataIsNullOrBlank() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void shouldRoundTrip_whenConvertingBackAndForth() {
        List<String> original = List.of("Gore", "Violência");

        String serialized = converter.convertToDatabaseColumn(original);
        List<String> restored = converter.convertToEntityAttribute(serialized);

        assertThat(restored).isEqualTo(original);
    }
}
