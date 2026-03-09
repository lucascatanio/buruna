package com.buruna.manga.repository;

import com.buruna.manga.domain.Manga;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MangaRepository extends JpaRepository<Manga, UUID> {

    Optional<Manga> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByTitleIgnoreCase(String title);

    @Query("""
            SELECT DISTINCT m FROM Manga m
            LEFT JOIN m.tags t
            WHERE m.isPublic = true
            AND (:title IS NULL OR (
                LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%'))
                OR LOWER(m.alternativeTitles) LIKE LOWER(CONCAT('%', :title, '%'))
            ))
            AND (:format IS NULL OR m.format = :format)
            AND (:statusOrigin IS NULL OR m.statusOrigin = :statusOrigin)
            AND (:tagIds IS NULL OR t.id IN :tagIds)
            """)
    Page<Manga> findPublicWithFilters(
            @Param("title") String title,
            @Param("format") com.buruna.manga.domain.MangaFormat format,
            @Param("statusOrigin") com.buruna.manga.domain.MangaStatusOrigin statusOrigin,
            @Param("tagIds") Set<UUID> tagIds,
            Pageable pageable
    );
}
