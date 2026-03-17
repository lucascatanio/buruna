package com.buruna.reader.repository;

import com.buruna.reader.domain.ReadingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReadingHistoryRepository extends JpaRepository<ReadingHistory, UUID> {

    @EntityGraph(attributePaths = {"volume", "volume.manga"})
    Page<ReadingHistory> findByUserIdOrderByReadAtDesc(UUID userId, Pageable pageable);
}