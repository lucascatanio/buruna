package com.buruna.sourcing.persistence;

import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.TrackedWork;
import com.buruna.sourcing.domain.TrackedWorkStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackedWorkRepository extends JpaRepository<TrackedWork, UUID> {

    boolean existsBySourceAndExternalId(SourceName source, String externalId);

    boolean existsByWorkId(UUID workId);

    Optional<TrackedWork> findByWorkId(UUID workId);

    List<TrackedWork> findBySourceAndStatus(SourceName source, TrackedWorkStatus status);

    List<TrackedWork> findAllByOrderByCreatedAtDesc();

    /** Serializa a gravação das versões de capítulo de uma obra acompanhada (ADR-53). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TrackedWork t where t.id = :id")
    Optional<TrackedWork> lockById(@Param("id") UUID id);
}
