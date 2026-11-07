package com.buruna.sourcing.application;

import com.buruna.shared.source.SourceChapterInfo;
import com.buruna.shared.source.SourceGroup;
import com.buruna.shared.source.mangadownloader.SiteListing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
