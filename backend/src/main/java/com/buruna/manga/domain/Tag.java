package com.buruna.manga.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tags")
@Getter
@NoArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private TagCategory category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public Tag(String name, String slug, TagCategory category) {
        this.name = name;
        this.slug = slug;
        this.category = category;
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public void rename(String name, String slug, TagCategory category) {
        this.name = name;
        this.slug = slug;
        this.category = category;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
