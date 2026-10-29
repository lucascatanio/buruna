package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.SourceChapterState;
import com.buruna.sourcing.domain.TrackedLanguage;
import com.buruna.sourcing.domain.TrackedWork;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Obra acompanhada, com quantas versões de capítulo estão em cada estado. */
public record TrackedWorkResponse(
        UUID id,
        UUID workId,
        String workTitle,
        String workSlug,
        String source,
        String externalId,
        String sourceUrl,
        String status,
        List<Language> languages,
        Map<String, Long> chapterCounts,
        OffsetDateTime lastCheckedAt,
        OffsetDateTime lastSuccessAt,
        int consecutiveFailures,
        String lastError,
        OffsetDateTime pausedAt,
        /** Preenchido só na pausa automática. */
        String pausedReason,
        List<Group> ignoredGroups,
        int alertCount,
        OffsetDateTime createdAt
) {

    public record Group(String id, String name) {
    }

    public record Language(String language, String preferredGroupId, String preferredGroupName) {
        static Language from(TrackedLanguage language) {
            return new Language(language.getLanguage(), language.getPreferredGroupId().orElse(null),
                    language.getPreferredGroupName());
        }
    }

    static TrackedWorkResponse from(TrackedWork tracked, String workTitle, String workSlug,
                                    Map<SourceChapterState, Long> counts, int alertCount) {
        return new TrackedWorkResponse(
                tracked.getId(), tracked.getWorkId(), workTitle, workSlug,
                tracked.getSource().name(), tracked.getExternalId(), SourceUrls.work(tracked),
                tracked.getStatus().name(),
                tracked.getLanguages().stream().map(Language::from).toList(),
                counts.entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey().name(), Map.Entry::getValue)),
                tracked.getLastCheckedAt(), tracked.getLastSuccessAt(), tracked.getConsecutiveFailures(),
                tracked.getLastError(), tracked.getPausedAt(), tracked.getPausedReason().orElse(null),
                tracked.getIgnoredGroups().stream().map(g -> new Group(g.getGroupId(), g.getGroupName())).toList(),
                alertCount, tracked.getCreatedAt());
    }
}
