package com.buruna.manga.repository;

import com.buruna.manga.domain.Volume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VolumeRepository extends JpaRepository<Volume, UUID> {

    boolean existsByFileHash(String fileHash);

    boolean existsByMangaIdAndVolumeNumber(UUID mangaId, Integer volumeNumber);

    Optional<Volume> findByIdAndMangaId(UUID id, UUID mangaId);
}