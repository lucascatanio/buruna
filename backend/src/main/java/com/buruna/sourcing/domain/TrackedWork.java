package com.buruna.sourcing.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Obra do Burūna acompanhada numa fonte externa (ADR-47). Guarda os idiomas acompanhados, o
 * grupo preferido de cada um e o estado da última verificação. Referencia a obra por id.
 */
@Entity
@Table(name = "tracked_works")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackedWork {

    private static final int MAX_ERROR_LENGTH = 2000;
    /** Falhas seguidas de listagem até a obra pausar sozinha (~1 dia com sync a cada 3 h). */
    public static final int FAILURES_BEFORE_PAUSE = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "work_id", nullable = false, updatable = false)
    private UUID workId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, updatable = false, columnDefinition = "external_source")
    private SourceName source;

    @Column(name = "external_id", nullable = false, updatable = false, length = 100)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "tracked_work_status")
    private TrackedWorkStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tracked_work_languages", joinColumns = @JoinColumn(name = "tracked_work_id"))
    private List<TrackedLanguage> languages = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tracked_work_ignored_groups", joinColumns = @JoinColumn(name = "tracked_work_id"))
    private Set<IgnoredGroup> ignoredGroups = new HashSet<>();

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    // verificação pedida (ADM ou sync) e ainda não feita
    @Column(name = "listing_requested", nullable = false)
    private boolean listingRequested;

    @Column(name = "paused_at")
    private OffsetDateTime pausedAt;

    @Column(name = "paused_reason", columnDefinition = "TEXT")
    private String pausedReason;

    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Começa a acompanhar uma obra. Precisa de ao menos um idioma, sem repetição. */
    public static TrackedWork track(UUID workId, SourceName source, String externalId,
                                    List<TrackedLanguage> languages, UUID createdBy) {
        if (externalId == null || externalId.isBlank()) {
            throw new InvalidTrackedWorkException("Id da obra na fonte obrigatório");
        }
        if (languages == null || languages.isEmpty()) {
            throw new InvalidTrackedWorkException("Escolha ao menos um idioma");
        }
        Set<String> seen = new HashSet<>();
        for (TrackedLanguage language : languages) {
            if (!seen.add(language.getLanguage())) {
                throw new InvalidTrackedWorkException("Idioma repetido: " + language.getLanguage());
            }
        }
        TrackedWork tracked = new TrackedWork();
        tracked.workId = workId;
        tracked.source = source;
        tracked.externalId = externalId;
        tracked.status = TrackedWorkStatus.ACTIVE;
        tracked.languages = new ArrayList<>(languages);
        tracked.createdBy = createdBy;
        return tracked;
    }

    public Set<String> languageTags() {
        Set<String> tags = new HashSet<>();
        languages.forEach(language -> tags.add(language.getLanguage()));
        return tags;
    }

    public Optional<String> preferredGroupFor(String language) {
        return languages.stream()
                .filter(l -> l.getLanguage().equals(language))
                .findFirst()
                .flatMap(TrackedLanguage::getPreferredGroupId);
    }

    /** Nunca listou os capítulos da fonte, ou alguém pediu uma verificação. */
    public boolean needsListing() {
        return lastCheckedAt == null || listingRequested;
    }

    public void requestListing() {
        listingRequested = true;
    }

    public boolean isIgnored(String groupId) {
        return ignoredGroups.stream().anyMatch(group -> group.getGroupId().equals(groupId));
    }

    public void ignoreGroup(String groupId, String groupName) {
        ignoredGroups.add(new IgnoredGroup(groupId, groupName));
    }

    public void stopIgnoringGroup(String groupId) {
        ignoredGroups.removeIf(group -> group.getGroupId().equals(groupId));
    }

    public Set<String> ignoredGroupIds() {
        Set<String> ids = new HashSet<>();
        ignoredGroups.forEach(group -> ids.add(group.getGroupId()));
        return ids;
    }

    /** Pausada sozinha depois de falhar seguidamente; nulo se ativa ou pausada pelo ADM. */
    public Optional<String> getPausedReason() {
        return Optional.ofNullable(pausedReason);
    }

    public boolean isActive() {
        return status == TrackedWorkStatus.ACTIVE;
    }

    public void recordSuccess(OffsetDateTime now) {
        lastCheckedAt = now;
        lastSuccessAt = now;
        listingRequested = false;
        consecutiveFailures = 0;
        lastError = null;
    }

    /** Depois de {@link #FAILURES_BEFORE_PAUSE} falhas seguidas, a obra pausa sozinha. */
    public void recordFailure(String error, OffsetDateTime now) {
        lastCheckedAt = now;
        listingRequested = false;
        consecutiveFailures++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        if (consecutiveFailures >= FAILURES_BEFORE_PAUSE && status == TrackedWorkStatus.ACTIVE) {
            status = TrackedWorkStatus.PAUSED;
            pausedAt = now;
            pausedReason = "Pausada depois de " + consecutiveFailures + " falhas seguidas ao verificar a fonte.";
        }
    }

    public void pause(OffsetDateTime now) {
        status = TrackedWorkStatus.PAUSED;
        pausedAt = now;
        pausedReason = null;
    }

    /** Retoma e já pede uma verificação; o contador de falhas recomeça. */
    public void resume() {
        status = TrackedWorkStatus.ACTIVE;
        pausedAt = null;
        pausedReason = null;
        consecutiveFailures = 0;
        listingRequested = true;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
