package com.buruna.work.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Capítulo de uma obra: a unidade de leitura (ADR-44). É agregado próprio e referencia a obra
 * por id, sem carregar a {@link Work}: gravar um capítulo não toca a obra (ADR-34, alterado).
 * A regra "número único por obra e idioma" depende de consulta e fica no
 * {@code RegisterChapterUseCase}, sob lock na obra (ADR-53).
 */
@Entity
@Table(name = "chapters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chapter {

    private static final int MAX_LABEL_LENGTH = 100;
    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "work_id", nullable = false, updatable = false)
    private UUID workId;

    @Column(nullable = false, length = 35)
    private String language;

    @Column(precision = 10, scale = 2)
    private BigDecimal number;

    @Column(length = MAX_LABEL_LENGTH)
    private String label;

    private String title;

    @Column(name = "scanlation_group")
    private String scanlationGroup;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "chapter_kind")
    private ChapterKind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "chapter_status")
    private ChapterStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedById;

    @Column(name = "source_object_name", length = 500)
    private String sourceObjectName;

    @Column(name = "source_size_bytes")
    private Long sourceSizeBytes;

    @ElementCollection
    @CollectionTable(name = "chapter_pages", joinColumns = @JoinColumn(name = "chapter_id"))
    @OrderBy("position ASC")
    private List<ChapterPage> pages = new ArrayList<>();

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ── Fábrica ──────────────────────────────────────────────────────────────

    /**
     * Registra um capítulo em processamento, antes de o conteúdo existir. Precisa de número ou
     * de rótulo para ser identificável na lista ("Cap. 12", "Vol. 3", "Extra").
     */
    public static Chapter register(UUID workId, Language language, ChapterNumber number, String label,
                                   String title, String scanlationGroup, ChapterKind kind, UUID uploadedById) {
        String trimmedLabel = blankToNull(label);
        if (number == null && trimmedLabel == null) {
            throw new InvalidChapterException("Capítulo precisa de número ou de rótulo");
        }
        if (trimmedLabel != null && trimmedLabel.length() > MAX_LABEL_LENGTH) {
            throw new InvalidChapterException("Rótulo do capítulo passa de " + MAX_LABEL_LENGTH + " caracteres");
        }
        Chapter chapter = new Chapter();
        chapter.workId = workId;
        chapter.language = language.value();
        chapter.number = number == null ? null : number.value();
        chapter.label = trimmedLabel;
        chapter.title = blankToNull(title);
        chapter.scanlationGroup = blankToNull(scanlationGroup);
        chapter.kind = kind;
        chapter.status = ChapterStatus.PROCESSING;
        chapter.uploadedById = uploadedById;
        return chapter;
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    /** Conclui um capítulo de imagens. As páginas precisam vir numeradas de 1 a N, sem buraco. */
    public void publishPages(List<ChapterPage> newPages, OffsetDateTime now) {
        if (kind != ChapterKind.PAGES) {
            throw new InvalidChapterException("Capítulo de arquivo não recebe páginas");
        }
        requireProcessing();
        if (newPages == null || newPages.isEmpty()) {
            throw new InvalidChapterException("Capítulo sem páginas");
        }
        List<ChapterPage> sorted = newPages.stream().sorted(Comparator.comparingInt(ChapterPage::getPosition)).toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getPosition() != i + 1) {
                throw new InvalidChapterException("Páginas precisam ser numeradas de 1 a " + sorted.size() + " sem repetição");
            }
        }
        pages.clear();
        pages.addAll(sorted);
        status = ChapterStatus.PUBLISHED;
        failureReason = null;
        publishedAt = now;
    }

    /**
     * Guarda o arquivo enviado (CBZ) de onde as páginas vão ser extraídas. Só um capítulo de
     * imagens em processamento recebe arquivo, uma vez.
     */
    public void attachSource(String objectName, long sizeBytes) {
        if (kind != ChapterKind.PAGES) {
            throw new InvalidChapterException("Capítulo de arquivo não recebe arquivo para extração");
        }
        requireProcessing();
        if (sourceObjectName != null) {
            throw new InvalidChapterException("Capítulo já tem arquivo enviado");
        }
        if (objectName == null || objectName.isBlank() || sizeBytes < 1) {
            throw new InvalidChapterException("Arquivo enviado inválido");
        }
        sourceObjectName = objectName;
        sourceSizeBytes = sizeBytes;
    }

    /**
     * Solta o arquivo enviado e devolve o nome dele, para quem chama apagar do storage depois
     * do commit. Vazio se o capítulo não tinha arquivo.
     */
    public Optional<String> discardSource() {
        Optional<String> discarded = Optional.ofNullable(sourceObjectName);
        sourceObjectName = null;
        sourceSizeBytes = null;
        return discarded;
    }

    /**
     * Volta um capítulo que falhou para processamento, reaproveitando o arquivo já enviado.
     * O número pode ter sido ocupado por outro capítulo nesse meio-tempo; isso é checado por
     * quem chama, sob lock na obra.
     */
    public void retry() {
        if (status != ChapterStatus.FAILED) {
            throw new InvalidChapterException("Só um capítulo que falhou pode ser processado de novo");
        }
        if (sourceObjectName == null) {
            throw new InvalidChapterException("O arquivo deste capítulo não foi guardado; envie de novo");
        }
        status = ChapterStatus.PROCESSING;
        failureReason = null;
    }

    /** O processamento não terminou; o número volta a ficar livre para uma nova tentativa. */
    public void fail(String reason) {
        requireProcessing();
        String trimmed = blankToNull(reason);
        status = ChapterStatus.FAILED;
        failureReason = trimmed == null || trimmed.length() <= MAX_FAILURE_REASON_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    private void requireProcessing() {
        if (status != ChapterStatus.PROCESSING) {
            throw new InvalidChapterException("Capítulo não está em processamento (status " + status + ")");
        }
    }

    // ── Leitura ──────────────────────────────────────────────────────────────

    public Language getLanguage() {
        return Language.of(language);
    }

    public Optional<ChapterNumber> getNumber() {
        return Optional.ofNullable(number).map(ChapterNumber::of);
    }

    public Optional<String> getLabel() {
        return Optional.ofNullable(label);
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public Optional<String> getScanlationGroup() {
        return Optional.ofNullable(scanlationGroup);
    }

    public Optional<String> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Optional<String> getSourceObjectName() {
        return Optional.ofNullable(sourceObjectName);
    }

    public long getSourceSizeBytes() {
        return sourceSizeBytes == null ? 0 : sourceSizeBytes;
    }

    public Optional<OffsetDateTime> getPublishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public List<ChapterPage> getPages() {
        return Collections.unmodifiableList(pages);
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chapter that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
