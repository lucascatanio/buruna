package com.buruna.work.persistence;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkSubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkRepository extends JpaRepository<Work, UUID>, JpaSpecificationExecutor<Work> {

    Page<Work> findAll(Specification<Work> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"tags", "tags.category"})
    Optional<Work> findBySlug(String slug);

    @EntityGraph(attributePaths = {"tags", "tags.category"})
    Optional<Work> findById(UUID id);

    Page<Work> findAllByOwnerIdAndIsPublicFalse(UUID ownerId, Pageable pageable);

    boolean existsBySlug(String slug);

    boolean existsByTitleIgnoreCaseAndIsPublicTrue(String title);

    @EntityGraph(attributePaths = {"tags", "tags.category"})
    List<Work> findAllWithTagsByIdIn(List<UUID> ids);

    List<Work> findByOwnerIdAndIsPublicFalse(UUID ownerId);

    // trava (FOR UPDATE) os mangás privados do dono para serializar a checagem de cota;
    // ORDER BY fixa a ordem de aquisição e evita deadlock entre transações concorrentes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Work m WHERE m.ownerId = :ownerId AND m.isPublic = false ORDER BY m.id")
    List<Work> lockPrivateByOwnerId(@Param("ownerId") UUID ownerId);

    Page<Work> findBySubmissionStatus(WorkSubmissionStatus status, Pageable pageable);
}
