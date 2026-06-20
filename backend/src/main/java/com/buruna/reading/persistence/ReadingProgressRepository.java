package com.buruna.reading.persistence;

import com.buruna.reading.domain.ReadingProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, UUID> {

    Optional<ReadingProgress> findByUserIdAndVolumeId(UUID userId, UUID volumeId);

    List<ReadingProgress> findByUserIdAndVolumeIdIn(UUID userId, List<UUID> volumeIds);
}
