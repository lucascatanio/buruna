package com.buruna.engagement.persistence;

import com.buruna.engagement.domain.ReadingList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingListRepository extends JpaRepository<ReadingList, UUID> {

    Optional<ReadingList> findByUserIdAndMangaId(UUID userId, UUID mangaId);

    List<ReadingList> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);

    void deleteByUserIdAndMangaId(UUID userId, UUID mangaId);

    boolean existsByUserIdAndMangaId(UUID userId, UUID mangaId);
}
