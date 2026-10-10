package com.buruna.work.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterTest {

    static final UUID WORK_ID = UUID.randomUUID();
    static final UUID UPLOADER_ID = UUID.randomUUID();
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-09T12:00:00Z");

    static Chapter pagesChapter() {
        return Chapter.register(WORK_ID, Language.of("pt-BR"), ChapterNumber.of(BigDecimal.ONE),
                null, "O começo", "Grupo X", ChapterKind.PAGES, UPLOADER_ID);
    }

    static ChapterPage page(int position) {
        return ChapterPage.of(position, "chapters/c/" + position + ".jpg", null, 800, 1200, 1024);
    }

    @Test
    void shouldStartProcessingWithoutPages_whenRegistered() {
        // Act
        Chapter chapter = pagesChapter();

        // Assert
        assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.PROCESSING);
        assertThat(chapter.getPages()).isEmpty();
        assertThat(chapter.getPublishedAt()).isEmpty();
        assertThat(chapter.getLanguage()).isEqualTo(Language.of("pt-BR"));
        assertThat(chapter.getScanlationGroup()).contains("Grupo X");
    }

    @Test
    void shouldAcceptLabelWithoutNumber_whenChapterIsAnExtra() {
        // Act
        Chapter chapter = Chapter.register(WORK_ID, Language.of("pt-BR"), null, "  Extra  ",
                null, null, ChapterKind.PAGES, UPLOADER_ID);

        // Assert
        assertThat(chapter.getNumber()).isEmpty();
        assertThat(chapter.getLabel()).contains("Extra");
    }

    @Test
    void shouldThrowInvalidChapter_whenNeitherNumberNorLabel() {
        assertThatThrownBy(() -> Chapter.register(WORK_ID, Language.of("pt-BR"), null, " ",
                null, null, ChapterKind.PAGES, UPLOADER_ID))
                .isInstanceOf(InvalidChapterException.class);
    }

    @Test
    void shouldPublishWithPagesInOrder_whenPagesArriveOutOfOrder() {
        // Arrange
        Chapter chapter = pagesChapter();

        // Act
        chapter.publishPages(List.of(page(2), page(1), page(3)), NOW);

        // Assert
        assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.PUBLISHED);
        assertThat(chapter.getPublishedAt()).contains(NOW);
        assertThat(chapter.getPages()).extracting(ChapterPage::getPosition).containsExactly(1, 2, 3);
    }

    @Test
    void shouldThrowInvalidChapter_whenPagesHaveAGap() {
        // Arrange
        Chapter chapter = pagesChapter();

        // Act / Assert
        assertThatThrownBy(() -> chapter.publishPages(List.of(page(1), page(3)), NOW))
                .isInstanceOf(InvalidChapterException.class);
        assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.PROCESSING);
    }

    @Test
    void shouldThrowInvalidChapter_whenPagesRepeatAPosition() {
        Chapter chapter = pagesChapter();

        assertThatThrownBy(() -> chapter.publishPages(List.of(page(1), page(1)), NOW))
                .isInstanceOf(InvalidChapterException.class);
    }

    @Test
    void shouldThrowInvalidChapter_whenPublishingWithoutPages() {
        Chapter chapter = pagesChapter();

        assertThatThrownBy(() -> chapter.publishPages(List.of(), NOW))
                .isInstanceOf(InvalidChapterException.class);
    }

    @Test
    void shouldThrowInvalidChapter_whenFileChapterReceivesPages() {
        // Arrange
        Chapter chapter = Chapter.register(WORK_ID, Language.of("pt-BR"), null, "Livro",
                null, null, ChapterKind.FILE, UPLOADER_ID);

        // Act / Assert
        assertThatThrownBy(() -> chapter.publishPages(List.of(page(1)), NOW))
                .isInstanceOf(InvalidChapterException.class);
    }

    @Test
    void shouldThrowInvalidChapter_whenPublishingTwice() {
        // Arrange
        Chapter chapter = pagesChapter();
        chapter.publishPages(List.of(page(1)), NOW);

        // Act / Assert
        assertThatThrownBy(() -> chapter.publishPages(List.of(page(1)), NOW))
                .isInstanceOf(InvalidChapterException.class);
    }

    @Test
    void shouldRecordReason_whenProcessingFails() {
        // Arrange
        Chapter chapter = pagesChapter();

        // Act
        chapter.fail("CBZ corrompido");

        // Assert
        assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.FAILED);
        assertThat(chapter.getFailureReason()).contains("CBZ corrompido");
    }

    @Test
    void shouldThrowInvalidChapter_whenFailingAPublishedChapter() {
        // Arrange
        Chapter chapter = pagesChapter();
        chapter.publishPages(List.of(page(1)), NOW);

        // Act / Assert
        assertThatThrownBy(() -> chapter.fail("tarde demais")).isInstanceOf(InvalidChapterException.class);
    }

    @Test
    void shouldThrowInvalidChapter_whenPageHasNoDimensions() {
        assertThatThrownBy(() -> ChapterPage.of(1, "chapters/c/1.jpg", null, 0, 1200, 1024))
                .isInstanceOf(InvalidChapterException.class);
    }
}
