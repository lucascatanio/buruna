package com.buruna.sourcing.application;

import com.buruna.shared.media.PageExtractor;
import com.buruna.shared.source.SourceChapterInfo;
import com.buruna.shared.source.SourceGroup;
import com.buruna.shared.source.mangadownloader.MangaDownloaderClient;
import com.buruna.shared.source.mangadownloader.SiteListing;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.TrackedLanguage;
import com.buruna.sourcing.domain.TrackedWork;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MangaDownloaderChapterSourceTest {

    @Test
    void shouldWriteNumberAsCliRange_whenNumberIsWholeOrDecimal() {
        assertThat(MangaDownloaderChapterSource.formatNumber(12.0)).isEqualTo("12");
        assertThat(MangaDownloaderChapterSource.formatNumber(34.5)).isEqualTo("34.5");
        assertThat(MangaDownloaderChapterSource.formatNumber(100)).isEqualTo("100");
        assertThat(MangaDownloaderChapterSource.formatNumber(0)).isEqualTo("0");
    }

    @Test
    void shouldDropLabelThatOnlyRepeatsNumber_whenTitleComesFromCli() {
        assertThat(MangaDownloaderChapterSource.titleOf("Capítulo 12", "12")).isEmpty();
        assertThat(MangaDownloaderChapterSource.titleOf("Chapter 0012: The End", "12")).contains("The End");
        assertThat(MangaDownloaderChapterSource.titleOf("Ch. 34.5 - Extra", "34.5")).contains("Extra");
        // o rótulo é de outro número (temporada própria do site): fica como veio
        assertThat(MangaDownloaderChapterSource.titleOf("Chapter 3", "103")).contains("Chapter 3");
        assertThat(MangaDownloaderChapterSource.titleOf("Especial", null)).contains("Especial");
        assertThat(MangaDownloaderChapterSource.titleOf(" ", "1")).isEmpty();
    }

    @Test
    void shouldEscapeGroupReversibly_whenNameHasCommaOrPercent() {
        for (String name : List.of("A, B Scans", "100% Scans", "Grupo %2C literal", "번역 팀, 둘", "Mais+Um")) {
            String id = MangaDownloaderChapterSource.groupId(name);

            assertThat(id).doesNotContain(",");
            assertThat(MangaDownloaderChapterSource.groupName(id)).isEqualTo(name);
        }
        // fora vírgula e %, o nome fica como está: em coreano, cabe nas colunas
        assertThat(MangaDownloaderChapterSource.groupId("번역 팀")).isEqualTo("번역 팀");
    }

    @Test
    void shouldTruncateSiteId_whenItPassesColumn() {
        SourceChapterInfo info = MangaDownloaderChapterSource.toInfo(
                new SiteListing.Chapter("/c/" + "x".repeat(600), 1, false, "Capítulo 1", "en", "", null, 0, false));

        assertThat(info.externalId()).hasSize(500);
    }

    @Test
    void shouldBuildKeyFromNumberLanguageAndGroup_whenSiteGivesNoId() {
        SourceChapterInfo info = MangaDownloaderChapterSource.toInfo(
                new SiteListing.Chapter("", 2, false, "Capítulo 2", "es", "A", List.of("A", "B, C"), 3, false));

        assertThat(info.externalId()).isEqualTo("sem-id:2|es|A,B, C");
        assertThat(info.number()).isEqualTo("2");
        assertThat(info.volume()).isEqualTo("3");
        assertThat(info.title()).isNull();
        assertThat(info.groups()).containsExactly(new SourceGroup("A", "A"), new SourceGroup("B%2C C", "B, C"));
    }

    @Test
    void shouldKeepSiteIdAndUseTitleAsKey_whenChapterHasNoNumber() {
        SourceChapterInfo withId = MangaDownloaderChapterSource.toInfo(
                new SiteListing.Chapter("/manga/x/extra", 0, true, "Extra", null, "", null, 0, false));
        SourceChapterInfo withoutId = MangaDownloaderChapterSource.toInfo(
                new SiteListing.Chapter(null, 0, true, "Extra", "en", "G", null, 0, false));

        assertThat(withId.externalId()).isEqualTo("/manga/x/extra");
        assertThat(withId.number()).isNull();
        assertThat(withId.language()).isEmpty();
        assertThat(withId.groups()).isEmpty();
        assertThat(withoutId.externalId()).isEqualTo("sem-id:#Extra|en|G");
        assertThat(withoutId.groups()).containsExactly(new SourceGroup("G", "G"));
    }

    private static final String URL = "https://mangafire.test/title/obra";

    static SiteListing listing(SiteListing.Filter languages, SiteListing.Chapter... chapters) {
        return new SiteListing("Obra", "Mangafire", List.of(chapters), languages, null, "");
    }

    static SiteListing.Chapter chapter(String id, double number, String language, boolean locked) {
        return new SiteListing.Chapter(id, number, false, "Chapter " + (int) number, language, "", null, 0, locked);
    }

    @Test
    void shouldListEachTrackedLanguageApart_whenAllOnlyBringsSiteDefault() {
        // como o mangafire.to: "all" é sem filtro e traz só o idioma padrão; o idioma pedido vem à parte,
        // com o campo do capítulo escrito do jeito do site
        MangaDownloaderClient client = mock(MangaDownloaderClient.class);
        when(client.list(URL, "pt-br")).thenReturn(listing(null,
                chapter("p1", 1, "Portuguese (Br)", false), chapter("p2", 2, "Portuguese (Br)", true)));
        when(client.list(URL, "en")).thenReturn(listing(null, chapter("e1", 1, "en", false)));
        TrackedWork tracked = TrackedWork.track(UUID.randomUUID(), SourceName.MANGA_DOWNLOADER, URL, List.of(
                TrackedLanguage.fromSite("pt-BR", "pt-br", null, null),
                TrackedLanguage.fromSite("en", "en", null, null)), UUID.randomUUID());

        List<SourceChapterInfo> versions = source(client).listChapters(tracked).versions();

        assertThat(versions).extracting(SourceChapterInfo::externalId).containsExactlyInAnyOrder("p1", "e1");
        assertThat(versions).filteredOn(v -> v.externalId().equals("p1"))
                .singleElement().extracting(SourceChapterInfo::language).isEqualTo("pt-br");
        verify(client, never()).list(URL);
    }

    @Test
    void shouldKeepOnlyChaptersWithoutLanguage_whenSiteGivesNone() {
        MangaDownloaderClient client = mock(MangaDownloaderClient.class);
        when(client.list(URL, "")).thenReturn(listing(null, chapter("a", 1, "", false), chapter("b", 2, "en", false)));
        TrackedWork tracked = TrackedWork.track(UUID.randomUUID(), SourceName.MANGA_DOWNLOADER, URL,
                List.of(TrackedLanguage.fromSite("es-419", "", null, null)), UUID.randomUUID());

        List<SourceChapterInfo> versions = source(client).listChapters(tracked).versions();

        assertThat(versions).extracting(SourceChapterInfo::externalId).containsExactly("a");
    }

    static MangaDownloaderChapterSource source(MangaDownloaderClient client) {
        return new MangaDownloaderChapterSource(client, mock(PageExtractor.class), 1000, 1024, 50);
    }

    static TrackedWork trackedIn(String language, String siteCode) {
        return TrackedWork.track(UUID.randomUUID(), SourceName.MANGA_DOWNLOADER, URL,
                List.of(TrackedLanguage.fromSite(language, siteCode, null, null)), UUID.randomUUID());
    }

    @Test
    void shouldReportEmptyLanguage_whenCodeIsBlankAndSiteListsOtherLanguages() {
        // o Jujutsu Kaisen do mangafire (#142): código vazio lista o padrão do site, que é inglês
        MangaDownloaderClient client = mock(MangaDownloaderClient.class);
        when(client.list(URL, "")).thenReturn(listing(new SiteListing.Filter(List.of("en", "es-la"), false),
                chapter("e1", 1, "en", false), chapter("e2", 2, "en", false)));

        ChapterListing result = source(client).listChapters(trackedIn("pt-BR", ""));

        assertThat(result.versions()).isEmpty();
        assertThat(result.emptyLanguages()).containsExactly(new ChapterListing.EmptyLanguage("", List.of("en", "es-la")));
        assertThat(result.notice()).contains(
                "Nenhum capítulo sem idioma informado; o site listou en, es-la. Confira o código no painel Idiomas.");
    }

    @Test
    void shouldReportEmptyLanguageWithFilterCodes_whenCodeListsNothing() {
        MangaDownloaderClient client = mock(MangaDownloaderClient.class);
        when(client.list(URL, "pt")).thenReturn(listing(new SiteListing.Filter(List.of("en", "pt-br", "pt"), false)));

        ChapterListing result = source(client).listChapters(trackedIn("pt-BR", "pt"));

        assertThat(result.notice()).contains(
                "Nenhum capítulo em pt; o site listou en, pt-br. Confira o código no painel Idiomas.");
    }

    @Test
    void shouldNotReport_whenLanguageHasOnlyLockedChaptersOrSiteListsNothingElse() {
        MangaDownloaderClient client = mock(MangaDownloaderClient.class);
        when(client.list(URL, "pt-br")).thenReturn(listing(new SiteListing.Filter(List.of("en", "pt-br"), false),
                chapter("p1", 1, "Portuguese (Br)", true)));
        when(client.list(URL, "en")).thenReturn(listing(null));

        ChapterListing result = source(client).listChapters(TrackedWork.track(UUID.randomUUID(),
                SourceName.MANGA_DOWNLOADER, URL, List.of(TrackedLanguage.fromSite("pt-BR", "pt-br", null, null),
                        TrackedLanguage.fromSite("en", "en", null, null)), UUID.randomUUID()));

        assertThat(result.versions()).isEmpty();
        assertThat(result.notice()).isEmpty();
    }

    @Test
    void shouldOfferSiteFilterLanguages_whenDefaultListingBringsOnlyOne() {
        MangaDownloaderClient client = mock(MangaDownloaderClient.class);
        when(client.list(URL)).thenReturn(listing(new SiteListing.Filter(List.of("en", "pt-br", "es-la"), false),
                chapter("e1", 1, "en", false)));
        TrackedWork tracked = TrackedWork.track(UUID.randomUUID(), SourceName.MANGA_DOWNLOADER, URL,
                List.of(TrackedLanguage.fromSite("en", "en", null, null)), UUID.randomUUID());

        assertThat(new MangaDownloaderChapterSource(client, mock(PageExtractor.class), 1000, 1024, 50)
                .availableLanguages(tracked)).containsExactlyInAnyOrder("en", "pt-br", "es-la");
    }
}
