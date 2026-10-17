package com.buruna.sourcing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Uma versão de capítulo publicada na fonte. O mesmo número pode ter várias (uma por grupo);
 * só a escolhida por {@link ChapterSelection} vira capítulo no Burūna. Referencia o capítulo
 * criado por id ({@code chapterId}), do contexto work.
 */
@Entity
@Table(name = "source_chapters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceChapter {

    private static final int MAX_TEXT = 255;
    private static final int MAX_GROUPS = 500;
    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tracked_work_id", nullable = false, updatable = false)
    private UUID trackedWorkId;

    @Column(name = "external_id", nullable = false, updatable = false, length = 100)
    private String externalId;

    @Column(nullable = false, length = 35)
    private String language;

    @Column(length = 20)
    private String number;

    @Column(length = 20)
    private String volume;

    private String title;

    // ids e nomes dos grupos, separados por vírgula e por " & " (só leitura e crédito)
    @Column(name = "group_ids", length = MAX_GROUPS)
    private String groupIds;

    @Column(name = "group_names", length = MAX_GROUPS)
    private String groupNames;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "external_updated_at")
    private OffsetDateTime externalUpdatedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "source_chapter_state")
    private SourceChapterState state;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Versão recém-descoberta; começa ignorada até a escolha de versões rodar. */
    public static SourceChapter discover(UUID trackedWorkId, String externalId, String language, String number,
                                         String volume, String title, List<String> groupIds, List<String> groupNames,
                                         int pageCount, OffsetDateTime publishedAt, OffsetDateTime externalUpdatedAt) {
        SourceChapter chapter = new SourceChapter();
        chapter.trackedWorkId = trackedWorkId;
        chapter.externalId = externalId;
        chapter.language = language;
        chapter.number = blankToNull(number);
        chapter.volume = blankToNull(volume);
        chapter.title = truncate(blankToNull(title), MAX_TEXT);
        chapter.groupIds = truncate(join(groupIds, ","), MAX_GROUPS);
        chapter.groupNames = truncate(join(groupNames, " & "), MAX_GROUPS);
        chapter.pageCount = pageCount;
        chapter.publishedAt = publishedAt;
        chapter.externalUpdatedAt = externalUpdatedAt;
        chapter.state = SourceChapterState.IGNORED;
        return chapter;
    }

    public boolean hasGroup(String groupId) {
        return groupIds != null && List.of(groupIds.split(",")).contains(groupId);
    }

    /**
     * Já passou pela escolha e foi adiante (baixada, em download ou com falha): o número fica
     * com esta versão até o ADM decidir outra coisa.
     */
    public boolean isTaken() {
        return chapterId != null || state == SourceChapterState.READY || state == SourceChapterState.FAILED;
    }

    public Optional<String> getNumber() {
        return Optional.ofNullable(number);
    }

    public Optional<UUID> getChapterId() {
        return Optional.ofNullable(chapterId);
    }

    /** Escolhida para entrar. Só sai de IGNORED: o que já foi baixado ou falhou não muda aqui. */
    public void select() {
        if (state == SourceChapterState.IGNORED) {
            state = SourceChapterState.PENDING;
        }
    }

    /** Outra versão do mesmo número foi escolhida. Só vale para quem ainda não foi baixada. */
    public void ignore() {
        if (state == SourceChapterState.PENDING && chapterId == null) {
            state = SourceChapterState.IGNORED;
        }
    }

    /** O capítulo do Burūna foi registrado (em processamento) para esta versão. */
    public void attachChapter(UUID chapterId) {
        this.chapterId = chapterId;
    }

    public void markReady() {
        state = SourceChapterState.READY;
        lastError = null;
    }

    /** O capítulo registrado para ela é descartado por quem chama; a versão fica sem capítulo. */
    public void fail(String reason) {
        state = SourceChapterState.FAILED;
        chapterId = null;
        lastError = truncate(reason, MAX_ERROR_LENGTH);
    }

    private static String join(List<String> values, String separator) {
        return values == null || values.isEmpty() ? null : String.join(separator, values);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
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
