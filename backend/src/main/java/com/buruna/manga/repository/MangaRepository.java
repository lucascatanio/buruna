package com.buruna.manga.repository;

import com.buruna.manga.domain.Manga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface MangaRepository extends JpaRepository<Manga, UUID>, JpaSpecificationExecutor<Manga> {

    Optional<Manga> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByTitleIgnoreCase(String title);
}
