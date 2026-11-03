package com.buruna.sourcing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Idioma acompanhado de uma obra, com o grupo de tradução preferido nele (opcional) e, quando a
 * fonte escreve o idioma do jeito dela, o código que ela usa ({@code sourceLanguage}).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackedLanguage {

    @Column(nullable = false, length = 35)
    private String language;

    @Column(name = "preferred_group_id", length = 100)
    private String preferredGroupId;

    @Column(name = "preferred_group_name")
    private String preferredGroupName;

    // nulo: a fonte usa a própria tag BCP 47 (MangaDex); vazio: o site não informa idioma
    @Column(name = "source_language", length = 35)
    private String sourceLanguage;

    private TrackedLanguage(String language, String preferredGroupId, String preferredGroupName,
                            String sourceLanguage) {
        this.language = language;
        this.preferredGroupId = preferredGroupId;
        this.preferredGroupName = preferredGroupName;
        this.sourceLanguage = sourceLanguage;
    }

    /** Idioma como tag BCP 47 ({@code pt-br} vira {@code pt-BR}). Grupo sem id = sem preferência. */
    public static TrackedLanguage of(String language, String preferredGroupId, String preferredGroupName) {
        boolean hasGroup = preferredGroupId != null && !preferredGroupId.isBlank();
        return new TrackedLanguage(normalize(language), hasGroup ? preferredGroupId : null,
                hasGroup ? preferredGroupName : null, null);
    }

    /**
     * Idioma de um site do manga-downloader (ADR-54): o ADM diz a que idioma do Burūna corresponde
     * o código do site ({@code es-la} → {@code es-419}); vazio para o site que não informa idioma.
     */
    public static TrackedLanguage fromSite(String language, String sourceLanguage, String preferredGroupId,
                                           String preferredGroupName) {
        TrackedLanguage tracked = of(language, preferredGroupId, preferredGroupName);
        tracked.sourceLanguage = sourceLanguage == null ? "" : sourceLanguage.trim();
        return tracked;
    }

    /** O idioma como a fonte o escreve. */
    public String sourceCode() {
        return sourceLanguage != null ? sourceLanguage : language;
    }

    private static String normalize(String language) {
        if (language == null || language.isBlank()) {
            throw new InvalidTrackedWorkException("Idioma obrigatório");
        }
        try {
            String tag = new Locale.Builder().setLanguageTag(language.trim()).build().toLanguageTag();
            if (!tag.equals("und")) {
                return tag;
            }
        } catch (IllformedLocaleException e) {
            // cai na exceção abaixo
        }
        throw new InvalidTrackedWorkException("Idioma inválido: " + language);
    }

    public Optional<String> getPreferredGroupId() {
        return Optional.ofNullable(preferredGroupId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackedLanguage that)) return false;
        return language.equals(that.language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(language);
    }
}
