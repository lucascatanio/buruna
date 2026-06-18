package com.buruna.engagement.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ratings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "manga_id"}))
@Getter
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "manga_id", nullable = false)
    private UUID mangaId;

    @Column(nullable = false)
    private int score;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected Rating() {}

    public static Rating create(UUID userId, UUID mangaId, Score score) {
        Rating r = new Rating();
        r.userId = userId;
        r.mangaId = mangaId;
        r.score = score.value();
        return r;
    }

    public void updateScore(Score newScore) {
        this.score = newScore.value();
    }
}
