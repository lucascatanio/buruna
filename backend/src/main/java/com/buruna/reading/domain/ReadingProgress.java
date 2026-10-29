package com.buruna.reading.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reading_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingProgress {

    static final BigDecimal FINISHED_PERCENT = new BigDecimal("0.99");
    private static final int MAX_POSITION_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Volume (legado) ou capítulo: exatamente um dos dois (V30). */
    @Column(name = "volume_id")
    private UUID volumeId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "current_page", nullable = false)
    private int currentPage = 1;

    /** Total de páginas do volume, informado pelo leitor; nulo enquanto não for conhecido. */
    @Column(name = "total_pages")
    private Integer totalPages;

    /** Num EPUB, que não tem página fixa: a posição (CFI) e o andamento de 0 a 1. */
    @Column(columnDefinition = "TEXT")
    private String position;

    @Column(precision = 5, scale = 4)
    private BigDecimal percent;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static ReadingProgress start(UUID userId, UUID volumeId) {
        ReadingProgress progress = new ReadingProgress();
        progress.userId = userId;
        progress.volumeId = volumeId;
        return progress;
    }

    public static ReadingProgress startChapter(UUID userId, UUID chapterId) {
        ReadingProgress progress = new ReadingProgress();
        progress.userId = userId;
        progress.chapterId = chapterId;
        return progress;
    }

    /**
     * Registra a página atual. O total é opcional: sem ele, vale o total já conhecido.
     * Página abaixo de 1, total abaixo de 1 ou página além do total são inválidos.
     */
    public void recordPage(int page, Integer total) {
        if (page < 1) {
            throw new InvalidReadingProgressException("Página inválida: " + page);
        }
        if (total != null && total < 1) {
            throw new InvalidReadingProgressException("Total de páginas inválido: " + total);
        }
        Integer effectiveTotal = total != null ? total : totalPages;
        if (effectiveTotal != null && page > effectiveTotal) {
            throw new InvalidReadingProgressException("Página " + page + " além do total de " + effectiveTotal);
        }
        this.totalPages = effectiveTotal;
        this.currentPage = page;
    }

    /**
     * Registra a posição num EPUB. {@code percent} vai de 0 a 1; o leitor de EPUB não chega a 1 na
     * última tela de um capítulo curto, por isso a partir de {@link #FINISHED_PERCENT} conta como lido.
     */
    public void recordPosition(String newPosition, BigDecimal newPercent) {
        if (newPosition == null || newPosition.isBlank() || newPosition.length() > MAX_POSITION_LENGTH) {
            throw new InvalidReadingProgressException("Posição de leitura inválida");
        }
        if (newPercent == null || newPercent.signum() < 0 || newPercent.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidReadingProgressException("Andamento inválido: " + newPercent);
        }
        this.position = newPosition;
        this.percent = newPercent.setScale(4, RoundingMode.HALF_UP);
    }

    /** Lido até o fim: última página conhecida ou, num EPUB, andamento quase completo. */
    public boolean isFinished() {
        if (percent != null) {
            return percent.compareTo(FINISHED_PERCENT) >= 0;
        }
        return totalPages != null && currentPage >= totalPages;
    }

    public Optional<String> getPosition() {
        return Optional.ofNullable(position);
    }

    public Optional<BigDecimal> getPercent() {
        return Optional.ofNullable(percent);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
