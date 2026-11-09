package com.buruna.sourcing.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackedWorkTest {

    static TrackedWork tracked(List<TrackedLanguage> languages) {
        return TrackedWork.track(UUID.randomUUID(), SourceName.MANGADEX, "md-1", languages, UUID.randomUUID());
    }

    @Test
    void shouldNormalizeLanguage_whenSourceCodeIsLowercase() {
        TrackedLanguage language = TrackedLanguage.of("pt-br", "g1", "Grupo");

        assertThat(language.getLanguage()).isEqualTo("pt-BR");
    }

    @Test
    void shouldDropGroupName_whenGroupIdIsBlank() {
        TrackedLanguage language = TrackedLanguage.of("en", " ", "Grupo");

        assertThat(language.getPreferredGroupId()).isEmpty();
        assertThat(language.getPreferredGroupName()).isNull();
    }

    @Test
    void shouldRejectTracking_whenNoLanguage() {
        assertThatThrownBy(() -> tracked(List.of())).isInstanceOf(InvalidTrackedWorkException.class);
    }

    @Test
    void shouldRejectTracking_whenLanguageRepeats() {
        assertThatThrownBy(() -> tracked(List.of(TrackedLanguage.of("pt-BR", null, null),
                TrackedLanguage.of("pt-br", null, null))))
                .isInstanceOf(InvalidTrackedWorkException.class);
    }

    @Test
    void shouldReturnPreferredGroup_whenLanguageHasOne() {
        TrackedWork tracked = tracked(List.of(TrackedLanguage.of("pt-BR", "g1", "Grupo"),
                TrackedLanguage.of("en", null, null)));

        assertThat(tracked.preferredGroupFor("pt-BR")).contains("g1");
        assertThat(tracked.preferredGroupFor("en")).isEmpty();
    }

    @Test
    void shouldResetFailures_whenCheckSucceeds() {
        TrackedWork tracked = tracked(List.of(TrackedLanguage.of("en", null, null)));
        OffsetDateTime now = OffsetDateTime.parse("2026-10-17T12:00:00Z");
        tracked.recordFailure("timeout", now);

        tracked.recordSuccess(now.plusHours(1), null);

        assertThat(tracked.getConsecutiveFailures()).isZero();
        assertThat(tracked.getLastError()).isNull();
        assertThat(tracked.needsListing()).isFalse();
    }

    @Test
    void shouldKeepNoticeUntilNextCleanListing_whenListingSucceedsWithNotice() {
        TrackedWork tracked = tracked(List.of(TrackedLanguage.of("en", null, null)));
        OffsetDateTime now = OffsetDateTime.parse("2026-11-07T12:00:00Z");

        tracked.recordSuccess(now, "Nenhum capítulo em pt-br; o site listou en.");

        assertThat(tracked.getListingNotice()).contains("Nenhum capítulo em pt-br; o site listou en.");
        tracked.recordSuccess(now.plusHours(1), " ");
        assertThat(tracked.getListingNotice()).isEmpty();
    }

    @Test
    void shouldReplaceSourceCodeAndKeepOtherLanguages_whenSiteLanguageIsTrackedAgain() {
        TrackedWork tracked = TrackedWork.track(UUID.randomUUID(), SourceName.MANGA_DOWNLOADER,
                "https://mangafire.test/manga/obra", List.of(TrackedLanguage.fromSite("pt-BR", "", "g1", "Grupo"),
                        TrackedLanguage.fromSite("en", "en", null, null)), UUID.randomUUID());

        tracked.trackLanguage(TrackedLanguage.fromSite("pt-BR", " pt-br ", "g1", "Grupo"));

        assertThat(tracked.sourceLanguageCodes()).containsExactlyInAnyOrder("pt-br", "en");
        assertThat(tracked.languageFromSource("pt-br")).contains("pt-BR");
        assertThat(tracked.languageFromSource("")).isEmpty();
        assertThat(tracked.preferredGroupFor("pt-BR")).contains("g1");
    }

    @Test
    void shouldPauseItself_whenListingFailsEightTimesInARow() {
        TrackedWork tracked = tracked(List.of(TrackedLanguage.of("en", null, null)));
        OffsetDateTime now = OffsetDateTime.parse("2026-10-17T12:00:00Z");

        for (int i = 0; i < TrackedWork.FAILURES_BEFORE_PAUSE - 1; i++) {
            tracked.recordFailure("timeout", now);
        }
        assertThat(tracked.isActive()).isTrue();
        tracked.recordFailure("timeout", now);

        assertThat(tracked.isActive()).isFalse();
        assertThat(tracked.getPausedReason()).hasValueSatisfying(reason -> assertThat(reason).contains("8 falhas"));
    }

    @Test
    void shouldClearFailuresAndAskForCheck_whenResumed() {
        TrackedWork tracked = tracked(List.of(TrackedLanguage.of("en", null, null)));
        OffsetDateTime now = OffsetDateTime.parse("2026-10-17T12:00:00Z");
        tracked.recordSuccess(now, null);
        tracked.pause(now);

        tracked.resume();

        assertThat(tracked.isActive()).isTrue();
        assertThat(tracked.getConsecutiveFailures()).isZero();
        assertThat(tracked.needsListing()).isTrue();
        assertThat(tracked.getPausedReason()).isEmpty();
    }

    @Test
    void shouldReportIgnoredGroup_whenAdminIgnoresIt() {
        TrackedWork tracked = tracked(List.of(TrackedLanguage.of("en", null, null)));

        tracked.ignoreGroup("g1", "Grupo");

        assertThat(tracked.isIgnored("g1")).isTrue();
        tracked.stopIgnoringGroup("g1");
        assertThat(tracked.isIgnored("g1")).isFalse();
    }

    @Test
    void shouldAddLanguageOrReplaceItsGroup_whenTrackingALanguage() {
        TrackedWork tracked = tracked(List.of(TrackedLanguage.of("pt-BR", "g1", "Grupo 1")));
        tracked.recordSuccess(OffsetDateTime.parse("2026-10-29T12:00:00Z"), null);

        tracked.trackLanguage(TrackedLanguage.of("en", null, null));
        tracked.trackLanguage(TrackedLanguage.of("pt-br", "g2", "Grupo 2"));

        assertThat(tracked.languageTags()).containsExactlyInAnyOrder("pt-BR", "en");
        assertThat(tracked.preferredGroupFor("pt-BR")).contains("g2");
        assertThat(tracked.needsListing()).isTrue();
    }

    // ── sites do manga-downloader (ADR-54) ──────────────────────────────────

    static TrackedWork site(String url, List<TrackedLanguage> languages) {
        return TrackedWork.track(UUID.randomUUID(), SourceName.MANGA_DOWNLOADER, url, languages, UUID.randomUUID());
    }

    @Test
    void shouldTranslateSiteLanguageCodes_whenAdminMappedThem() {
        TrackedWork tracked = site("https://site.test/obra", List.of(
                TrackedLanguage.fromSite("es-419", "es-la", "A", "A"),
                TrackedLanguage.fromSite("pt-BR", null, null, null)));

        assertThat(tracked.sourceLanguageCodes()).containsExactlyInAnyOrder("es-la", "");
        assertThat(tracked.languageFromSource("ES-LA")).contains("es-419");
        // site que não informa idioma: vazio e nulo caem no idioma declarado pelo ADM
        assertThat(tracked.languageFromSource("")).contains("pt-BR");
        assertThat(tracked.languageFromSource(null)).contains("pt-BR");
        assertThat(tracked.languageFromSource("en")).isEmpty();
        assertThat(tracked.sourceCodeFor("es-419")).contains("es-la");
        assertThat(tracked.preferredGroupFor("es-419")).contains("A");
    }

    @Test
    void shouldUseTagAsSourceCode_whenSourceIsMangaDex() {
        TrackedWork tracked = tracked(List.of(TrackedLanguage.of("pt-br", null, null)));

        assertThat(tracked.sourceLanguageCodes()).containsExactly("pt-BR");
        assertThat(tracked.languageFromSource("pt-BR")).contains("pt-BR");
    }

    @Test
    void shouldRejectSeries_whenUrlIsNotHttp() {
        List<TrackedLanguage> languages = List.of(TrackedLanguage.fromSite("en", "en", null, null));

        assertThatThrownBy(() -> site("--output-dir=/tmp", languages)).isInstanceOf(InvalidTrackedWorkException.class);
        assertThatThrownBy(() -> site("ftp://site.test/obra", languages)).isInstanceOf(InvalidTrackedWorkException.class);
        assertThatThrownBy(() -> site("https://" + "a".repeat(500) + ".test", languages))
                .isInstanceOf(InvalidTrackedWorkException.class);
    }

    @Test
    void shouldShareMangaDexLease_onlyForMangaDexSeriesOnSites() {
        List<TrackedLanguage> languages = List.of(TrackedLanguage.fromSite("en", "en", null, null));

        assertThat(site("https://mangadex.org/title/abc", languages).sharesMangaDexLease()).isTrue();
        assertThat(site("https://www.mangadex.org/title/abc", languages).sharesMangaDexLease()).isTrue();
        assertThat(site("https://notmangadex.org/title/abc", languages).sharesMangaDexLease()).isFalse();
        assertThat(tracked(languages).sharesMangaDexLease()).isFalse();
    }
}
