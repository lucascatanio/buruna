package com.buruna.manga.persistence;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.MangaSubmissionStatus;
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

public interface MangaRepository extends JpaRepository<Manga, UUID>, JpaSpecificationExecutor<Manga> {

    Page<Manga> findAll(Specification<Manga> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"tags", "tags.category"})
    Optional<Manga> findBySlug(String slug);

    @EntityGraph(attributePaths = {"tags", "tags.category"})
    Optional<Manga> findById(UUID id);

    Page<Manga> findAllByOwnerIdAndIsPublicFalse(UUID ownerId, Pageable pageable);

    boolean existsBySlug(String slug);

    boolean existsByTitleIgnoreCaseAndIsPublicTrue(String title);

    @EntityGraph(attributePaths = {"tags", "tags.category"})
    List<Manga> findAllWithTagsByIdIn(List<UUID> ids);

    List<Manga> findByOwnerIdAndIsPublicFalse(UUID ownerId);

    // trava (FOR UPDATE) os mangás privados do dono para serializar a checagem de cota;
    // ORDER BY fixa a ordem de aquisição e evita deadlock entre transações concorrentes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Manga m WHERE m.ownerId = :ownerId AND m.isPublic = false ORDER BY m.id")
    List<Manga> lockPrivateByOwnerId(@Param("ownerId") UUID ownerId);

    Page<Manga> findBySubmissionStatus(MangaSubmissionStatus status, Pageable pageable);
}
