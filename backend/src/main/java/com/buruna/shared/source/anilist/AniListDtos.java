package com.buruna.shared.source.anilist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Recorte das respostas da API GraphQL do AniList: só os campos que o adaptador usa. */
final class AniListDtos {

    private AniListDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(SearchData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchData(Page page) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page(List<Media> media) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MediaResponse(MediaData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MediaData(Media media) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Media(
            Long id,
            Title title,
            List<String> synonyms,
            String description,
            FuzzyDate startDate,
            String status,
            String countryOfOrigin,
            String format,
            Boolean isAdult,
            List<String> genres,
            List<Tag> tags,
            CoverImage coverImage,
            String siteUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Title(String romaji, String english, String nativeTitle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FuzzyDate(Integer year) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tag(String name, Integer rank, Boolean isMediaSpoiler, Boolean isGeneralSpoiler) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CoverImage(String medium, String extraLarge) {
    }
}
