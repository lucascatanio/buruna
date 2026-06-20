package com.buruna.reading.persistence;

import com.buruna.reading.domain.ReadingProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, UUID> {

    Optional<ReadingProgress> findByUserIdAndVolumeId(UUID userId, UUID volumeId);

    List<ReadingProgress> findByUserIdAndVolumeIdIn(UUID userId, List<UUID> volumeIds);

    // Native SQL: join com a tabela volumes sem importar entidades de outro contexto (ADR-35)
    @Query(value = """
            SELECT rp.*
            FROM reading_progress rp
            JOIN volumes v ON v.id = rp.volume_id
            WHERE rp.user_id = :userId AND v.manga_id = :mangaId
            ORDER BY v.volume_number DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ReadingProgress> findLatestByUserIdAndMangaId(
            @Param("userId") UUID userId,
            @Param("mangaId") UUID mangaId);
}
