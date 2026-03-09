package com.buruna.manga.service;

import com.buruna.manga.domain.Tag;
import com.buruna.manga.domain.TagCategory;
import com.buruna.manga.dto.*;
import com.buruna.manga.exception.TagAlreadyExistsException;
import com.buruna.manga.exception.TagCategoryAlreadyExistsException;
import com.buruna.manga.exception.TagCategoryNotFoundException;
import com.buruna.manga.exception.TagNotFoundException;
import com.buruna.manga.repository.TagCategoryRepository;
import com.buruna.manga.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final TagCategoryRepository tagCategoryRepository;

    public List<TagResponse> findAllActiveTags() {
        return tagRepository.findAllActiveWithCategory()
                .stream()
                .map(TagResponse::from)
                .toList();
    }

    public List<TagCategoryResponse> findAllCategories() {
        return tagCategoryRepository.findAll()
                .stream()
                .map(TagCategoryResponse::from)
                .toList();
    }

    @Transactional
    public TagCategoryResponse createCategory(TagCategoryRequest request) {
        if (tagCategoryRepository.existsByName(request.name())) {
            throw new TagCategoryAlreadyExistsException(request.name());
        }
        TagCategory category = new TagCategory();
        category.setName(request.name());
        return TagCategoryResponse.from(tagCategoryRepository.save(category));
    }

    @Transactional
    public TagResponse createTag(TagRequest request) {
        if (tagRepository.existsBySlug(request.slug())) {
            throw new TagAlreadyExistsException(request.slug());
        }
        TagCategory category = tagCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new TagCategoryNotFoundException(request.categoryId()));

        Tag tag = new Tag();
        tag.setName(request.name());
        tag.setSlug(request.slug());
        tag.setCategory(category);
        return TagResponse.from(tagRepository.save(tag));
    }

    @Transactional
    public TagResponse updateTag(UUID id, TagRequest request) {
        Tag tag = findActiveTagById(id);

        if (!tag.getSlug().equals(request.slug()) && tagRepository.existsBySlug(request.slug())) {
            throw new TagAlreadyExistsException(request.slug());
        }
        TagCategory category = tagCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new TagCategoryNotFoundException(request.categoryId()));

        tag.setName(request.name());
        tag.setSlug(request.slug());
        tag.setCategory(category);
        return TagResponse.from(tagRepository.save(tag));
    }

    @Transactional
    public void deleteTag(UUID id) {
        Tag tag = findActiveTagById(id);
        tag.setDeletedAt(OffsetDateTime.now());
        tagRepository.save(tag);
    }

    private Tag findActiveTagById(UUID id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new TagNotFoundException(id));
        if (!tag.isActive()) {
            throw new TagNotFoundException(id);
        }
        return tag;
    }
}
