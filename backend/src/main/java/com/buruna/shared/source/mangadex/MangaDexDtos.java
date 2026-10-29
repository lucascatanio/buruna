package com.buruna.shared.source.mangadex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** Recorte das respostas da API do MangaDex: só os campos que o adaptador usa. */
final class MangaDexDtos {

    private MangaDexDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaList(List<Manga> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaResponse(Manga data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Manga(String id, MangaAttributes attributes, List<Relationship> relationships) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaAttributes(
            Map<String, String> title,
            List<Map<String, String>> altTitles,
            Map<String, String> description,
            String originalLanguage,
            String status,
            Integer year,
            String contentRating,
            String publicationDemographic,
            List<String> availableTranslatedLanguages,
            List<Tag> tags
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tag(TagAttributes attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TagAttributes(Map<String, String> name, String group) {
    }

    /** {@code fileName} só vem em cover_art e {@code name} só em scanlation_group. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Relationship(String id, String type, RelationshipAttributes attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RelationshipAttributes(String fileName, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChapterFeed(List<Chapter> data, Integer total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chapter(String id, ChapterAttributes attributes, List<Relationship> relationships) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChapterAttributes(
            String volume,
            String chapter,
            String title,
            String translatedLanguage,
            String externalUrl,
            Integer pages,
            // capítulo retirado (pedido de remoção, por exemplo): continua no feed, sem páginas
            Boolean isUnavailable,
            String publishAt,
            String updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AtHomeResponse(String baseUrl, AtHomeChapter chapter) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AtHomeChapter(String hash, List<String> data, List<String> dataSaver) {
    }
}
