package com.buruna.manga.persistence;

import com.buruna.manga.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    @Query("SELECT t FROM Tag t JOIN FETCH t.category WHERE t.deletedAt IS NULL ORDER BY t.category.name, t.name")
    List<Tag> findAllActiveWithCategory();

    boolean existsBySlug(String slug);
}
