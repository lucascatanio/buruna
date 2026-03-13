package com.buruna.engagement.repository;

import com.buruna.engagement.domain.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    Optional<Rating> findByUserIdAndMangaId(UUID userId, UUID mangaId);

    void deleteByUserIdAndMangaId(UUID userId, UUID mangaId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.manga.id = :mangaId")
    int countByMangaId(@Param("mangaId") UUID mangaId);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM Rating r WHERE r.manga.id = :mangaId")
    double avgScoreByMangaId(@Param("mangaId") UUID mangaId);
}