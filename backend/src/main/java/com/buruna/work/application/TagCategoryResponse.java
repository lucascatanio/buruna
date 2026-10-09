package com.buruna.work.application;

import com.buruna.work.domain.TagCategory;

import java.util.UUID;

public record TagCategoryResponse(UUID id, String name) {
    public static TagCategoryResponse from(TagCategory category) {
        return new TagCategoryResponse(category.getId(), category.getName());
    }
}
