package com.buruna.manga.controller;

import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.dto.MangaRequest;
import com.buruna.manga.dto.MangaResponse;
import com.buruna.manga.service.MangaService;
import com.buruna.identity.domain.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/mangas")
public class MangaController {

    private final MangaService mangaService;

    public MangaController(MangaService mangaService) {
        this.mangaService = mangaService;
    }

    @GetMapping
    public ResponseEntity<Page<MangaResponse>> listPublic(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) MangaFormat format,
            @RequestParam(required = false) MangaStatusOrigin statusOrigin,
            @RequestParam(required = false) Set<UUID> tagIds,
            @PageableDefault(size = 20, sort = "title") Pageable pageable
    ) {
        return ResponseEntity.ok(
                mangaService.findPublic(title, format, statusOrigin, tagIds, pageable));
    }

    @GetMapping("/{slugOrId}")
    public ResponseEntity<MangaResponse> getBySlugOrId(@PathVariable String slugOrId) {
        return ResponseEntity.ok(mangaService.findBySlugOrId(slugOrId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<MangaResponse> create(
            @Valid @RequestBody MangaRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mangaService.create(request, currentUser));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<MangaResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody MangaRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(mangaService.update(id, request, currentUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        mangaService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}