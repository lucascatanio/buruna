package com.buruna.manga.controller;

import com.buruna.manga.dto.VolumeResponse;
import com.buruna.manga.service.VolumeService;
import com.buruna.user.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<VolumeResponse> upload(
            @PathVariable UUID mangaId,
            @RequestParam Integer volumeNumber,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(volumeService.upload(mangaId, volumeNumber, file, currentUser));
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