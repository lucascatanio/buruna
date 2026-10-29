package com.buruna.work.persistence;

import com.buruna.work.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    @Query("SELECT t FROM Tag t JOIN FETCH t.category WHERE t.deletedAt IS NULL ORDER BY t.category.name, t.name")
    List<Tag> findAllActiveWithCategory();

    boolean existsBySlug(String slug);

    Optional<Tag> findBySlug(String slug);

    Optional<Tag> findFirstByNameIgnoreCase(String name);
}
