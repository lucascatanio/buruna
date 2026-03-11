package com.buruna.manga.repository;

import com.buruna.manga.domain.Manga;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

public interface MangaRepository extends JpaRepository<Manga, UUID>, JpaSpecificationExecutor<Manga> {

    @EntityGraph(attributePaths = {"tags", "tags.category"})
    Page<Manga> findAll(Specification<Manga> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"tags", "tags.category"})
    Optional<Manga> findBySlug(String slug);

    @EntityGraph(attributePaths = {"tags", "tags.category"})
    Optional<Manga> findById(UUID id);

    boolean existsBySlug(String slug);

    boolean existsByTitleIgnoreCase(String title);
}
