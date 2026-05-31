package com.buruna.manga.domain;

import com.buruna.infra.converter.StringListConverter;
import com.buruna.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "mangas")
@Getter
@Setter
@NoArgsConstructor
public class Manga {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Convert(converter = StringListConverter.class)
    @Column(name = "alternative_titles", columnDefinition = "TEXT")
    private List<String> alternativeTitles = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "manga_format")
    private MangaFormat format;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status_origin", nullable = false, columnDefinition = "manga_status_origin")
    private MangaStatusOrigin statusOrigin;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status_site", nullable = false, columnDefinition = "manga_status_site")
    private MangaStatusSite statusSite;

    private Integer year;

    @Convert(converter = StringListConverter.class)
    @Column(name = "content_warnings", columnDefinition = "TEXT")
    private List<String> contentWarnings = new ArrayList<>();

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "submission_status", columnDefinition = "manga_submission_status")
    private MangaSubmissionStatus submissionStatus;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "manga_tags",
            joinColumns = @JoinColumn(name = "manga_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "manga", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("volumeNumber ASC")
    private List<Volume> volumes = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Manga manga)) return false;
        return id != null && id.equals(manga.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
