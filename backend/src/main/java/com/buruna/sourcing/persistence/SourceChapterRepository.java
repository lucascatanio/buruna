package com.buruna.sourcing.persistence;

import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.SourceChapterState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SourceChapterRepository extends JpaRepository<SourceChapter, UUID> {

    List<SourceChapter> findByTrackedWorkId(UUID trackedWorkId);

    List<SourceChapter> findByTrackedWorkIdInAndState(Collection<UUID> trackedWorkIds, SourceChapterState state);

    long countByTrackedWorkIdAndState(UUID trackedWorkId, SourceChapterState state);
}
