package com.buruna.manga.repository;

import com.buruna.manga.domain.Volume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VolumeRepository extends JpaRepository<Volume, UUID> {

    boolean existsByMangaIdAndVolumeNumber(UUID mangaId, Integer volumeNumber);

    boolean existsByFileHash(String fileHash);

    Optional<Volume> findByIdAndMangaId(UUID id, UUID mangaId);

    List<Volume> findByMangaId(UUID mangaId);

    /** Retorna IDs dos volumes de um mangá ordenados por volume_number DESC (para o contexto reading). */
    @Query("SELECT v.id FROM Volume v WHERE v.manga.id = :mangaId ORDER BY v.volumeNumber DESC")
    List<UUID> findIdsByMangaIdOrderByVolumeNumberDesc(@Param("mangaId") UUID mangaId);

    // soma os bytes de todos os volumes privados do usuário, usado para validação de cota
    @Query("SELECT COALESCE(SUM(v.fileSizeBytes), 0) FROM Volume v " +
            "WHERE v.manga.ownerId = :ownerId AND v.manga.isPublic = false")
    long sumPrivateFileSizeByOwnerId(@Param("ownerId") UUID ownerId);

    boolean existsByFileHashAndMangaIsPublicTrue(String fileHash);

    @Query("""
    SELECT v.manga.ownerId AS ownerId, SUM(v.fileSizeBytes) AS totalBytes
    FROM Volume v
    WHERE v.manga.isPublic = false
    GROUP BY v.manga.ownerId
    """)
    List<VolumeStorageProjection> findStorageByOwner();
}