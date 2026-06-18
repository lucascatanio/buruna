package com.buruna.engagement.persistence;

import com.buruna.engagement.domain.ReadingList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingListRepository extends JpaRepository<ReadingList, UUID> {

    Optional<ReadingList> findByUserIdAndMangaId(UUID userId, UUID mangaId);

    @Query("""
            SELECT rl FROM ReadingList rl
            JOIN FETCH rl.manga m
            WHERE rl.user.id = :userId
            ORDER BY rl.updatedAt DESC
            """)
    List<ReadingList> findAllByUserIdWithManga(@Param("userId") UUID userId);

    void deleteByUserIdAndMangaId(UUID userId, UUID mangaId);

    boolean existsByUserIdAndMangaId(UUID userId, UUID mangaId);
}
