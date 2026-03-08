package com.buruna.manga.controller;

import com.buruna.manga.dto.*;
import com.buruna.manga.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping("/tags")
    public ResponseEntity<List<TagResponse>> findAllTags() {
        return ResponseEntity.ok(tagService.findAllActiveTags());
    }

    @GetMapping("/tag-categories")
    public ResponseEntity<List<TagCategoryResponse>> findAllCategories() {
        return ResponseEntity.ok(tagService.findAllCategories());
    }

    @PostMapping("/tag-categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TagCategoryResponse> createCategory(@Valid @RequestBody TagCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.createCategory(request));
    }

    @PostMapping("/tags")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TagResponse> createTag(@Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.createTag(request));
    }

    @PutMapping("/tags/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TagResponse> updateTag(@PathVariable UUID id, @Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(tagService.updateTag(id, request));
    }

    @DeleteMapping("/tags/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTag(@PathVariable UUID id) {
        tagService.deleteTag(id);
        return ResponseEntity.noContent().build();
    }
}
