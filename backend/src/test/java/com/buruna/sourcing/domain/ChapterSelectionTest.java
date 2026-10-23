package com.buruna.sourcing.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterSelectionTest {

    private static final UUID TRACKED = UUID.randomUUID();
    private static final OffsetDateTime MARCH = OffsetDateTime.parse("2026-03-01T00:00:00Z");
    private static final OffsetDateTime MAY = OffsetDateTime.parse("2026-05-01T00:00:00Z");

    static SourceChapter version(String id, String number, String group, OffsetDateTime publishedAt) {
        return SourceChapter.discover(TRACKED, id, "pt-BR", number, null, null,
                List.of(group), List.of("Grupo " + group), 10, publishedAt, publishedAt);
    }

    @Test
    void shouldPickPreferredGroup_whenItHasTheChapter() {
        SourceChapter preferred = version("a", "10", "A", MARCH);
        SourceChapter other = version("b", "10", "B", MAY);

        ChapterSelection.apply(List.of(preferred, other), language -> Optional.of("A"), Set.of());

        assertThat(preferred.getState()).isEqualTo(SourceChapterState.PENDING);
        assertThat(other.getState()).isEqualTo(SourceChapterState.IGNORED);
    }

    @Test
    void shouldPickMostRecent_whenPreferredGroupDidNotTranslate() {
        SourceChapter older = version("b", "12", "B", MARCH);
        SourceChapter newer = version("c", "12", "C", MAY);

        ChapterSelection.apply(List.of(older, newer), language -> Optional.of("A"), Set.of());

        assertThat(newer.getState()).isEqualTo(SourceChapterState.PENDING);
        assertThat(older.getState()).isEqualTo(SourceChapterState.IGNORED);
    }

    @Test
    void shouldSkipIgnoredGroupAndRemovedVersion_whenChoosing() {
        SourceChapter ignoredGroup = version("a", "7", "A", MAY);
        SourceChapter removed = version("b", "7", "B", MAY);
        removed.missingAtSource(MAY);
        SourceChapter fallback = version("c", "7", "C", MARCH);

        ChapterSelection.apply(List.of(ignoredGroup, removed, fallback), language -> Optional.of("A"), Set.of("A"));

        assertThat(fallback.getState()).isEqualTo(SourceChapterState.PENDING);
        assertThat(ignoredGroup.getState()).isEqualTo(SourceChapterState.IGNORED);
        assertThat(removed.getState()).isEqualTo(SourceChapterState.IGNORED);
    }

    static SourceChapter extra(String id, String title, String group, OffsetDateTime publishedAt) {
        return SourceChapter.discover(TRACKED, id, "pt-BR", null, null, title,
                List.of(group), List.of("Grupo " + group), 10, publishedAt, publishedAt);
    }

    @Test
    void shouldPickOneVersion_whenTwoGroupsTranslatedTheSameOneshot() {
        SourceChapter first = extra("x", null, "B", MARCH);
        SourceChapter second = extra("y", null, "C", MAY);

        ChapterSelection.apply(List.of(first, second), language -> Optional.of("B"), Set.of());

        assertThat(first.getState()).isEqualTo(SourceChapterState.PENDING);
        assertThat(second.getState()).isEqualTo(SourceChapterState.IGNORED);
    }

    @Test
    void shouldPickEveryExtra_whenTitlesDiffer() {
        SourceChapter first = extra("x", "Especial de Natal", "B", MARCH);
        SourceChapter second = extra("y", "Especial de Ano Novo", "B", MAY);

        ChapterSelection.apply(List.of(first, second), language -> Optional.empty(), Set.of());

        assertThat(first.getState()).isEqualTo(SourceChapterState.PENDING);
        assertThat(second.getState()).isEqualTo(SourceChapterState.PENDING);
    }

    @Test
    void shouldKeepDownloadedVersion_whenPreferredGroupPublishesLater() {
        SourceChapter downloaded = version("b", "10", "B", MARCH);
        downloaded.select();
        downloaded.attachChapter(UUID.randomUUID());
        downloaded.markReady();
        SourceChapter preferredLate = version("a", "10", "A", MAY);

        ChapterSelection.apply(List.of(downloaded, preferredLate), language -> Optional.of("A"), Set.of());

        assertThat(downloaded.getState()).isEqualTo(SourceChapterState.READY);
        assertThat(preferredLate.getState()).isEqualTo(SourceChapterState.IGNORED);
    }
}
