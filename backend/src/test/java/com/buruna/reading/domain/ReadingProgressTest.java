package com.buruna.reading.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadingProgressTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID volumeId = UUID.randomUUID();

    @Test
    void shouldStartOnFirstPageWithoutTotal_whenCreated() {
        // Act
        ReadingProgress progress = ReadingProgress.start(userId, volumeId);

        // Assert
        assertThat(progress.getCurrentPage()).isEqualTo(1);
        assertThat(progress.getTotalPages()).isNull();
        assertThat(progress.isFinished()).isFalse();
    }

    @Test
    void shouldRecordPageAndTotal_whenBothAreValid() {
        // Arrange
        ReadingProgress progress = ReadingProgress.start(userId, volumeId);

        // Act
        progress.recordPage(88, 192);

        // Assert
        assertThat(progress.getCurrentPage()).isEqualTo(88);
        assertThat(progress.getTotalPages()).isEqualTo(192);
        assertThat(progress.isFinished()).isFalse();
    }

    @Test
    void shouldBeFinished_whenCurrentPageIsTheLastOne() {
        // Arrange
        ReadingProgress progress = ReadingProgress.start(userId, volumeId);

        // Act
        progress.recordPage(192, 192);

        // Assert
        assertThat(progress.isFinished()).isTrue();
    }

    @Test
    void shouldKeepKnownTotal_whenTotalIsNotInformed() {
        // Arrange
        ReadingProgress progress = ReadingProgress.start(userId, volumeId);
        progress.recordPage(10, 192);

        // Act
        progress.recordPage(20, null);

        // Assert
        assertThat(progress.getCurrentPage()).isEqualTo(20);
        assertThat(progress.getTotalPages()).isEqualTo(192);
    }

    @Test
    void shouldThrowInvalidReadingProgress_whenPageIsBelowOne() {
        // Arrange
        ReadingProgress progress = ReadingProgress.start(userId, volumeId);

        // Act + Assert
        assertThatThrownBy(() -> progress.recordPage(0, 192))
                .isInstanceOf(InvalidReadingProgressException.class);
    }

    @Test
    void shouldThrowInvalidReadingProgress_whenTotalIsBelowOne() {
        // Arrange
        ReadingProgress progress = ReadingProgress.start(userId, volumeId);

        // Act + Assert
        assertThatThrownBy(() -> progress.recordPage(1, 0))
                .isInstanceOf(InvalidReadingProgressException.class);
    }

    @Test
    void shouldThrowInvalidReadingProgress_whenPageIsBeyondTotal() {
        // Arrange
        ReadingProgress progress = ReadingProgress.start(userId, volumeId);

        // Act + Assert
        assertThatThrownBy(() -> progress.recordPage(193, 192))
                .isInstanceOf(InvalidReadingProgressException.class);
    }

    @Test
    void shouldThrowInvalidReadingProgress_whenPageIsBeyondKnownTotalAndTotalIsOmitted() {
        // Arrange
        ReadingProgress progress = ReadingProgress.start(userId, volumeId);
        progress.recordPage(10, 192);

        // Act + Assert
        assertThatThrownBy(() -> progress.recordPage(193, null))
                .isInstanceOf(InvalidReadingProgressException.class);
    }

    @Test
    void shouldCountAsFinished_whenEpubReachesTheEnd() {
        // Arrange
        ReadingProgress progress = ReadingProgress.startChapter(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());

        // Act
        progress.recordPosition("epubcfi(/6/14!/4/2/1:0)", new java.math.BigDecimal("0.995"));

        // Assert
        org.assertj.core.api.Assertions.assertThat(progress.isFinished()).isTrue();
        org.assertj.core.api.Assertions.assertThat(progress.getPosition()).contains("epubcfi(/6/14!/4/2/1:0)");
    }

    @Test
    void shouldNotBeFinished_whenEpubIsHalfway() {
        ReadingProgress progress = ReadingProgress.startChapter(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());

        progress.recordPosition("epubcfi(/6/8!/4/2/1:0)", new java.math.BigDecimal("0.5"));

        org.assertj.core.api.Assertions.assertThat(progress.isFinished()).isFalse();
    }

    @Test
    void shouldThrowInvalidReadingProgress_whenPercentIsAboveOne() {
        ReadingProgress progress = ReadingProgress.startChapter(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> progress.recordPosition("epubcfi(/6/2)", new java.math.BigDecimal("1.2")))
                .isInstanceOf(InvalidReadingProgressException.class);
    }
}
