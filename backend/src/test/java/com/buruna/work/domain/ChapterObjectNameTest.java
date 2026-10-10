package com.buruna.work.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterObjectNameTest {

    static final UUID WORK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void shouldRoundTripToSourceName_whenPendingBelongsToTheWork() {
        // Arrange
        String pending = ChapterObjectName.pendingFor(WORK_ID);

        // Act
        ChapterObjectName parsed = ChapterObjectName.parsePending(pending, WORK_ID);

        // Assert
        assertThat(parsed.pendingObjectName()).isEqualTo(pending);
        assertThat(parsed.sourceObjectName())
                .isEqualTo(pending.replaceFirst("^pending/chapters/", "chapter-sources/"));
    }

    @Test
    void shouldThrowInvalidObjectName_whenPendingBelongsToAnotherWork() {
        String pendingOfOther = ChapterObjectName.pendingFor(UUID.randomUUID());

        assertThatThrownBy(() -> ChapterObjectName.parsePending(pendingOfOther, WORK_ID))
                .isInstanceOf(InvalidChapterObjectNameException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "chapter-sources/11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222.cbz",
            "pending/chapters/11111111-1111-1111-1111-111111111111/../x.cbz",
            "pending/chapters/11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222.cbz/extra",
            "pending/volumes/11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222.pdf",
            "pending/chapters/11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222.zip"
    })
    void shouldThrowInvalidObjectName_whenNameIsNotAPendingChapterUpload(String raw) {
        assertThatThrownBy(() -> ChapterObjectName.parsePending(raw, WORK_ID))
                .isInstanceOf(InvalidChapterObjectNameException.class);
    }

    @Test
    void shouldBuildDeterministicPageName_whenSamePageIsWrittenTwice() {
        UUID chapterId = UUID.randomUUID();

        assertThat(ChapterObjectName.page(chapterId, 3, "png"))
                .isEqualTo(ChapterObjectName.page(chapterId, 3, "png"))
                .isEqualTo("chapters/" + chapterId + "/3.png");
    }
}
