package com.buruna.manga.application;

import com.buruna.manga.domain.Tag;

import java.util.UUID;

public record TagResponse(UUID id, String name, String slug, TagCategoryResponse category) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getSlug(),
                TagCategoryResponse.from(tag.getCategory())
        );
    }
}
