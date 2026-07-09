package com.buruna.manga.controller;

import com.buruna.manga.application.CatalogQueryUseCase;
import com.buruna.manga.application.CreatePublicMangaUseCase;
import com.buruna.manga.application.DeleteMangaUseCase;
import com.buruna.manga.application.GetMangaUseCase;
import com.buruna.manga.application.UpdateMangaUseCase;
import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.dto.MangaRequest;
import com.buruna.manga.dto.MangaResponse;
import com.buruna.identity.domain.Role;
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

    private final CatalogQueryUseCase catalogQuery;
    private final GetMangaUseCase getManga;
    private final CreatePublicMangaUseCase createPublicManga;
    private final UpdateMangaUseCase updateManga;
    private final DeleteMangaUseCase deleteManga;

    public MangaController(CatalogQueryUseCase catalogQuery,
                          GetMangaUseCase getManga,
                          CreatePublicMangaUseCase createPublicManga,
                          UpdateMangaUseCase updateManga,
                          DeleteMangaUseCase deleteManga) {
        this.catalogQuery = catalogQuery;
        this.getManga = getManga;
        this.createPublicManga = createPublicManga;
        this.updateManga = updateManga;
        this.deleteManga = deleteManga;
    }

    @GetMapping
    public ResponseEntity<Page<MangaResponse>> listPublic(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) MangaFormat format,
            @RequestParam(required = false) MangaStatusOrigin statusOrigin,
            @RequestParam(required = false) Set<UUID> tagIds,
            @PageableDefault(size = 20, sort = "title") Pageable pageable
    ) {
        return ResponseEntity.ok(catalogQuery.handle(title, format, statusOrigin, tagIds, pageable));
    }

    @GetMapping("/{slugOrId}")
    public ResponseEntity<MangaResponse> getBySlugOrId(@PathVariable String slugOrId) {
        return ResponseEntity.ok(getManga.handle(slugOrId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<MangaResponse> create(
            @Valid @RequestBody MangaRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createPublicManga.handle(request, currentUser.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<MangaResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody MangaRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(updateManga.handle(
                id, request, currentUser.getId(), currentUser.getRole() == Role.ADMIN));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        deleteManga.handle(id, currentUser.getId(), currentUser.getRole() == Role.ADMIN);
        return ResponseEntity.noContent().build();
    }
}
