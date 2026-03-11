package com.buruna.reader.repository;

import com.buruna.reader.domain.ReadingProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, UUID> {

    Optional<ReadingProgress> findByUserIdAndVolumeId(UUID userId, UUID volumeId);

    // progresso atual do usuário em um mangá: volume com maior número já lido
    @Query("""
            SELECT rp FROM ReadingProgress rp
            JOIN rp.volume v
            WHERE rp.user.id = :userId
            AND v.manga.id = :mangaId
            ORDER BY v.volumeNumber DESC
            LIMIT 1
            """)
    Optional<ReadingProgress> findLatestByUserIdAndMangaId(
            @Param("userId") UUID userId,
            @Param("mangaId") UUID mangaId);
}