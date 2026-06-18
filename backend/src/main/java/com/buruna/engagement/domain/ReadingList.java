package com.buruna.engagement.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reading_list",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "manga_id"}))
@Getter
public class ReadingList {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "manga_id", nullable = false)
    private UUID mangaId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "reading_list_status")
    private ReadingStatus status;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected ReadingList() {}

    public static ReadingList create(UUID userId, UUID mangaId, ReadingStatus status) {
        ReadingList rl = new ReadingList();
        rl.userId = userId;
        rl.mangaId = mangaId;
        rl.status = status;
        return rl;
    }

    public void updateStatus(ReadingStatus newStatus) {
        this.status = newStatus;
    }
}
