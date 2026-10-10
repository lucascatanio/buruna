package com.buruna.reading.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reading_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingProgress {

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

    /** Lido até a última página (só se sabe quando o total é conhecido). */
    public boolean isFinished() {
        return totalPages != null && currentPage >= totalPages;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
