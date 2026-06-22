package com.buruna.manga.controller;

import com.buruna.manga.dto.*;
import com.buruna.manga.service.PrivateMangaService;
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

import java.util.UUID;

@RestController
@RequestMapping("/my/mangas")
public class PrivateMangaController {

    private final PrivateMangaService privateMangaService;

    public PrivateMangaController(PrivateMangaService privateMangaService) {
        this.privateMangaService = privateMangaService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrivateMangaResponse> findById(@PathVariable UUID id,
                                                         @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(privateMangaService.findById(id, user));
    }

    @GetMapping
    public ResponseEntity<Page<PrivateMangaResponse>> listMine(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(privateMangaService.findAllByOwner(currentUser, pageable));
    }

    @GetMapping("/quota")
    public ResponseEntity<QuotaInfo> getQuota(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(privateMangaService.getQuotaInfo(currentUser));
    }

    @PostMapping
    public ResponseEntity<PrivateMangaResponse> create(
            @Valid @RequestBody PrivateMangaCreateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        PrivateMangaResponse response = privateMangaService.createManga(
                request.title(), request.synopsis(), request.coverBase64(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/volumes/upload-url")
    public ResponseEntity<VolumeUploadUrlResponse> getUploadUrl(
            @PathVariable UUID id,
            @Valid @RequestBody VolumeUploadUrlRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                privateMangaService.generateUploadUrl(id, request.volumeNumber(), currentUser));
    }

    @PostMapping("/{id}/volumes/finalize")
    public ResponseEntity<PrivateMangaResponse> finalizeVolume(
            @PathVariable UUID id,
            @Valid @RequestBody VolumeFinalizeRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(privateMangaService.finalizeVolume(id, request, currentUser));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrivateMangaResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PrivateMangaRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(privateMangaService.update(id, request, currentUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        privateMangaService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/volumes/{volumeId}")
    public ResponseEntity<PrivateMangaResponse> deleteVolume(
            @PathVariable UUID id,
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(privateMangaService.deleteVolume(id, volumeId, currentUser));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<PrivateMangaResponse> submitForApproval(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(privateMangaService.submitForApproval(id, currentUser));
    }

    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    @PostMapping("/{id}/promote")
    public ResponseEntity<PrivateMangaResponse> promote(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(privateMangaService.promote(id, currentUser));
    }
}
