package com.buruna.shared.source.mangadex;

import java.util.Locale;
import java.util.Objects;

/**
 * Converte os códigos de idioma do MangaDex de/para tags BCP 47.
 * Diferenças: {@code es-la} é {@code es-419}, e o sufixo {@code -ro} (romanizado) é o script
 * latino ({@code ja-ro} → {@code ja-Latn}). Regiões ({@code pt-br}) ganham caixa alta.
 */
public final class MangaDexLanguages {

    private static final String LATIN_AMERICAN_SPANISH = "es-la";
    private static final String LATIN_AMERICAN_SPANISH_BCP47 = "es-419";
    private static final String ROMANIZED_SUFFIX = "-ro";
    private static final String LATIN_SCRIPT_SUFFIX = "-latn";

    private MangaDexLanguages() {
    }

    /** {@code pt-br} → {@code pt-BR}, {@code es-la} → {@code es-419}, {@code ja-ro} → {@code ja-Latn}. */
    public static String toBcp47(String mangaDexCode) {
        String code = Objects.requireNonNull(mangaDexCode, "mangaDexCode").toLowerCase(Locale.ROOT);
        if (code.equals(LATIN_AMERICAN_SPANISH)) {
            return LATIN_AMERICAN_SPANISH_BCP47;
        }
        if (code.endsWith(ROMANIZED_SUFFIX)) {
            return code.substring(0, code.length() - ROMANIZED_SUFFIX.length()) + "-Latn";
        }
        int dash = code.indexOf('-');
        if (dash > 0 && code.length() - dash - 1 == 2) {
            return code.substring(0, dash) + "-" + code.substring(dash + 1).toUpperCase(Locale.ROOT);
        }
        return code;
    }

    /** Caminho de volta, para montar {@code translatedLanguage[]}: {@code pt-BR} → {@code pt-br}. */
    public static String toMangaDex(String bcp47Tag) {
        String tag = Objects.requireNonNull(bcp47Tag, "bcp47Tag").toLowerCase(Locale.ROOT);
        if (tag.equals(LATIN_AMERICAN_SPANISH_BCP47)) {
            return LATIN_AMERICAN_SPANISH;
        }
        if (tag.endsWith(LATIN_SCRIPT_SUFFIX)) {
            return tag.substring(0, tag.length() - LATIN_SCRIPT_SUFFIX.length()) + ROMANIZED_SUFFIX;
        }
        return tag;
    }
}
