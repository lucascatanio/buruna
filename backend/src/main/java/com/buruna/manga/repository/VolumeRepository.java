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

    // soma os bytes de todos os volumes privados do usuário, usado para validação de cota
    @Query("SELECT COALESCE(SUM(v.fileSizeBytes), 0) FROM Volume v " +
            "WHERE v.manga.owner.id = :ownerId AND v.manga.isPublic = false")
    long sumPrivateFileSizeByOwnerId(@Param("ownerId") UUID ownerId);
}