package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.ChapterSelection;
import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.SourceChapterState;
import com.buruna.sourcing.domain.TrackedWork;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Calcula os avisos de uma obra acompanhada a partir das versões dela. */
final class SourceAlerts {

    private SourceAlerts() {
    }

    static List<SourceAlert> of(TrackedWork tracked, Collection<SourceChapter> versions) {
        List<SourceAlert> alerts = new ArrayList<>();
        for (SourceChapter version : versions) {
            if (version.getState() == SourceChapterState.FAILED && version.getAttempts() >= SourceChapter.MAX_ATTEMPTS) {
                alerts.add(alert(tracked, version, SourceAlert.Type.FAILED, version.getLastError(), version.getFailedAt()));
            }
            if (version.isRemovedAtSource() && version.getChapterId().isPresent()) {
                alerts.add(alert(tracked, version, SourceAlert.Type.REMOVED_AT_SOURCE, null, version.getRemovedAtSource()));
            }
            if (version.getSourceUpdateDetectedAt() != null && version.getState() == SourceChapterState.READY) {
                alerts.add(alert(tracked, version, SourceAlert.Type.PAGES_CHANGED, null, version.getSourceUpdateDetectedAt()));
            }
        }
        alerts.addAll(preferredAvailable(tracked, versions));
        alerts.sort(Comparator.comparing(SourceAlert::detectedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return alerts;
    }

    // versão do grupo preferido que ficou de fora porque o número já tinha entrado por outro grupo
    private static List<SourceAlert> preferredAvailable(TrackedWork tracked, Collection<SourceChapter> versions) {
        Map<String, List<SourceChapter>> slots = versions.stream()
                .collect(Collectors.groupingBy(ChapterSelection::slotOf));
        List<SourceAlert> alerts = new ArrayList<>();
        for (List<SourceChapter> slot : slots.values()) {
            Optional<String> preferred = tracked.preferredGroupFor(slot.get(0).getLanguage());
            if (preferred.isEmpty()) {
                continue;
            }
            Optional<SourceChapter> holder = slot.stream()
                    .filter(v -> v.getState() == SourceChapterState.READY && v.getChapterId().isPresent()).findFirst();
            if (holder.isEmpty() || holder.get().hasGroup(preferred.get())) {
                continue;
            }
            slot.stream()
                    .filter(v -> v.getState() == SourceChapterState.IGNORED && !v.isRemovedAtSource()
                            && v.hasGroup(preferred.get()) && !v.hasAnyGroup(tracked.ignoredGroupIds()))
                    .max(Comparator.comparing(SourceChapter::getCreatedAt))
                    .ifPresent(v -> alerts.add(alert(tracked, v, SourceAlert.Type.PREFERRED_AVAILABLE,
                            holder.get().getGroupNames(), v.getCreatedAt())));
        }
        return alerts;
    }

    private static SourceAlert alert(TrackedWork tracked, SourceChapter version, SourceAlert.Type type,
                                     String detail, OffsetDateTime detectedAt) {
        return new SourceAlert(version.getId(), tracked.getId(), type, version.getLanguage(),
                version.getNumber().orElse(null), version.getTitle(), version.getGroupNames(), detail, detectedAt);
    }
}
