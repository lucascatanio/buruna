package com.buruna.manga.persistence;

import com.buruna.manga.domain.TagCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TagCategoryRepository extends JpaRepository<TagCategory, UUID> {
    boolean existsByName(String name);
}
