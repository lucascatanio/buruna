package com.buruna.sourcing.application;

import com.buruna.shared.source.SourceContentRating;
import com.buruna.shared.source.SourceTag;
import com.buruna.shared.source.SourceWorkDetails;
import com.buruna.work.application.ImportedWorkCommand;
import com.buruna.work.application.TagSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converte os metadados da fonte nos campos de uma obra do Burūna. */
final class WorkMetadataMapper {

    private static final String DEFAULT_SYNOPSIS_LANGUAGE = "pt-BR";
    private static final String FALLBACK_SYNOPSIS_LANGUAGE = "en";

    // o formulário da obra usa o nome do país em inglês ("Japan", "Korea")
    private static final Map<String, String> COUNTRIES = Map.of(
            "ja", "Japan", "ko", "Korea", "zh", "China", "zh-HK", "Hong Kong");

    private static final Map<String, String> FORMAT_TAGS = Map.of(
            "MANGA", "Mangá", "MANHWA", "Manhwa", "MANHUA", "Manhua");

    private WorkMetadataMapper() {
    }

    static ImportedWorkCommand toCommand(SourceWorkDetails details, String coverDataUri) {
        String format = formatOf(details);
        return new ImportedWorkCommand(
                titleOf(details),
                details.alternativeTitles(),
                synopsisOf(details.descriptions()),
                coverDataUri,
                format,
                COUNTRIES.get(details.originalLanguage()),
                details.status() == null ? "ONGOING" : details.status().name(),
                details.year(),
                contentWarningsOf(details),
                tagsOf(details, format));
    }

    // obra sem título na fonte: o primeiro título alternativo, senão o id
    private static String titleOf(SourceWorkDetails details) {
        if (details.title() != null && !details.title().isBlank()) {
            return details.title();
        }
        return details.alternativeTitles().stream().filter(t -> t != null && !t.isBlank()).findFirst()
                .orElse(details.externalId());
    }

    /** Idioma original decide manhwa e manhua; tira longa sem ser coreana nem chinesa é webtoon. */
    static String formatOf(SourceWorkDetails details) {
        String language = details.originalLanguage() == null ? "" : details.originalLanguage();
        if (language.equals("ko")) {
            return "MANHWA";
        }
        if (language.equals("zh") || language.startsWith("zh-")) {
            return "MANHUA";
        }
        if (details.longStrip()) {
            return "WEBTOON";
        }
        boolean oneshot = details.tags().stream().anyMatch(tag -> tag.name().equals("Oneshot"));
        return oneshot ? "ONESHOT" : "MANGA";
    }

    private static String synopsisOf(Map<String, String> descriptions) {
        if (descriptions == null || descriptions.isEmpty()) {
            return null;
        }
        if (descriptions.containsKey(DEFAULT_SYNOPSIS_LANGUAGE)) {
            return plainText(descriptions.get(DEFAULT_SYNOPSIS_LANGUAGE));
        }
        if (descriptions.containsKey(FALLBACK_SYNOPSIS_LANGUAGE)) {
            return plainText(descriptions.get(FALLBACK_SYNOPSIS_LANGUAGE));
        }
        return plainText(descriptions.values().iterator().next());
    }

    /**
     * A descrição do MangaDex vem em Markdown, e a sinopse do Burūna é texto puro: tira o bloco
     * de links do fim (depois de "---"), troca {@code [texto](url)} pelo texto e remove os
     * marcadores de ênfase.
     */
    static String plainText(String markdown) {
        String text = markdown.split("\\R\\s*-{3,}\\s*\\R", 2)[0];
        text = text.replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(\\*\\*|__|\\*|(?<=\\s|^)_|_(?=\\s|$))", "")
                .replaceAll("\\R{3,}", "\n\n");
        return text.strip();
    }

    // valores de CONTENT_WARNING_OPTIONS do formulário da obra
    private static List<String> contentWarningsOf(SourceWorkDetails details) {
        List<String> warnings = new ArrayList<>();
        if (details.contentRating() == SourceContentRating.EROTICA
                || details.contentRating() == SourceContentRating.PORNOGRAPHIC) {
            warnings.add("NSFW");
        }
        if (details.tags().stream().anyMatch(tag -> tag.name().equals("Gore"))) {
            warnings.add("GORE");
        }
        return warnings;
    }

    private static List<TagSpec> tagsOf(SourceWorkDetails details, String format) {
        List<TagSpec> tags = new ArrayList<>();
        for (SourceTag tag : details.tags()) {
            tags.add(SourceTagTranslations.translate(tag));
        }
        if (details.demographic() != null && !details.demographic().isBlank()) {
            tags.add(SourceTagTranslations.demographic(details.demographic()));
        }
        if (FORMAT_TAGS.containsKey(format)) {
            tags.add(new TagSpec(FORMAT_TAGS.get(format), SourceTagTranslations.FORMAT_CATEGORY));
        }
        return tags;
    }
}
