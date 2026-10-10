package com.buruna.reading.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reading_history")
@Getter
@Setter
public class ReadingHistory {

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

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt;

    @PrePersist
    void onCreate() {
        readAt = OffsetDateTime.now();
    }
}
