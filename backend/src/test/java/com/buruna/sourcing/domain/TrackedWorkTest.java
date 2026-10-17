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

        tracked.recordSuccess(now.plusHours(1));

        assertThat(tracked.getConsecutiveFailures()).isZero();
        assertThat(tracked.getLastError()).isNull();
        assertThat(tracked.needsListing()).isFalse();
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
        tracked.recordSuccess(now);
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
}
