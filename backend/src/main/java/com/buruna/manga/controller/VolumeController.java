package com.buruna.manga.controller;

import com.buruna.manga.dto.VolumeFinalizeRequest;
import com.buruna.manga.dto.VolumeResponse;
import com.buruna.manga.dto.VolumeUploadUrlRequest;
import com.buruna.manga.dto.VolumeUploadUrlResponse;
import com.buruna.manga.service.VolumeService;
import com.buruna.identity.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/mangas/{mangaId}/volumes")
public class VolumeController {

    private final VolumeService volumeService;

    public VolumeController(VolumeService volumeService) {
        this.volumeService = volumeService;
    }

    @GetMapping
    public ResponseEntity<List<VolumeResponse>> list(@PathVariable UUID mangaId) {
        return ResponseEntity.ok(volumeService.findByMangaId(mangaId));
    }

    @PostMapping("/upload-url")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<VolumeUploadUrlResponse> getUploadUrl(
            @PathVariable UUID mangaId,
            @Valid @RequestBody VolumeUploadUrlRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(
                volumeService.generateUploadUrl(mangaId, request.volumeNumber(), user));
    }

    @PostMapping("/finalize")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<VolumeResponse> finalize(
            @PathVariable UUID mangaId,
            @Valid @RequestBody VolumeFinalizeRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(volumeService.finalize(mangaId, request, user));
    }

    @DeleteMapping("/{volumeId}")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID mangaId,
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User currentUser
    ) {
        volumeService.delete(mangaId, volumeId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
