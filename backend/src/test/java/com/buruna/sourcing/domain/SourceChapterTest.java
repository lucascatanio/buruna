package com.buruna.sourcing.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SourceChapterTest {

    private static final OffsetDateTime MARCH = OffsetDateTime.parse("2026-03-01T00:00:00Z");
    private static final OffsetDateTime MAY = OffsetDateTime.parse("2026-05-01T00:00:00Z");

    static SourceChapter downloaded() {
        SourceChapter version = SourceChapter.discover(UUID.randomUUID(), "x", "pt-BR", "1", null, null,
                List.of("g"), List.of("Grupo"), 10, MARCH, MARCH);
        version.select();
        version.attachChapter(UUID.randomUUID());
        version.markReady();
        return version;
    }

    @Test
    void shouldFlagChangedPages_whenSourceUpdatedAfterDownload() {
        SourceChapter version = downloaded();

        version.seenAtSource(MAY, MAY);

        assertThat(version.getSourceUpdateDetectedAt()).isEqualTo(MAY);
    }

    @Test
    void shouldNotFlag_whenSourceIsNotNewer() {
        SourceChapter version = downloaded();

        version.seenAtSource(MARCH, MAY);

        assertThat(version.getSourceUpdateDetectedAt()).isNull();
    }

    @Test
    void shouldKeepChapter_whenDownloadedVersionDisappearsFromSource() {
        SourceChapter version = downloaded();

        version.missingAtSource(MAY);

        assertThat(version.isRemovedAtSource()).isTrue();
        assertThat(version.getState()).isEqualTo(SourceChapterState.READY);
        assertThat(version.getChapterId()).isPresent();
    }

    @Test
    void shouldStopRetrying_whenAttemptsRunOut() {
        SourceChapter version = SourceChapter.discover(UUID.randomUUID(), "x", "pt-BR", "1", null, null,
                List.of("g"), List.of("Grupo"), 10, MARCH, MARCH);
        version.select();
        for (int i = 0; i < SourceChapter.MAX_ATTEMPTS; i++) {
            version.fail("rede", MAY);
            if (version.canRetryAutomatically()) {
                version.retry(false);
            }
        }

        assertThat(version.getState()).isEqualTo(SourceChapterState.FAILED);
        assertThat(version.canRetryAutomatically()).isFalse();
        version.retry(true);
        assertThat(version.getState()).isEqualTo(SourceChapterState.PENDING);
        assertThat(version.getAttempts()).isZero();
    }

    @Test
    void shouldStayInChapterWithOldPages_whenRedownloadFails() {
        SourceChapter version = downloaded();
        UUID chapterId = version.getChapterId().orElseThrow();
        version.requestReplacement(chapterId);

        version.fail("nó fora do ar", MAY);

        assertThat(version.getState()).isEqualTo(SourceChapterState.READY);
        assertThat(version.getChapterId()).contains(chapterId);
        assertThat(version.getReplacesChapterId()).isEmpty();
    }

    @Test
    void shouldTakeTheChapter_whenReplacementSucceeds() {
        SourceChapter other = SourceChapter.discover(UUID.randomUUID(), "y", "pt-BR", "1", null, null,
                List.of("h"), List.of("Outro"), 10, MAY, MAY);
        UUID chapterId = UUID.randomUUID();
        other.requestReplacement(chapterId);

        other.replacedInto(chapterId, MAY);

        assertThat(other.getState()).isEqualTo(SourceChapterState.READY);
        assertThat(other.getChapterId()).contains(chapterId);
        assertThat(other.getReplacesChapterId()).isEmpty();
    }
}
