package com.buruna.engagement.persistence;

import com.buruna.engagement.domain.ReadingList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingListRepository extends JpaRepository<ReadingList, UUID> {

    Optional<ReadingList> findByUserIdAndWorkId(UUID userId, UUID workId);

    List<ReadingList> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);

    void deleteByUserIdAndWorkId(UUID userId, UUID workId);

    boolean existsByUserIdAndWorkId(UUID userId, UUID workId);
}
