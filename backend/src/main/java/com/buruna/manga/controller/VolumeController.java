package com.buruna.manga.controller;

import com.buruna.manga.application.DeletePublicVolumeUseCase;
import com.buruna.manga.application.FinalizePublicVolumeUseCase;
import com.buruna.manga.application.GeneratePublicVolumeUploadUrlUseCase;
import com.buruna.manga.application.ListPublicVolumesUseCase;
import com.buruna.manga.dto.VolumeFinalizeRequest;
import com.buruna.manga.dto.VolumeResponse;
import com.buruna.manga.dto.VolumeUploadUrlRequest;
import com.buruna.manga.dto.VolumeUploadUrlResponse;
import com.buruna.identity.domain.Role;
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

    private final ListPublicVolumesUseCase listPublicVolumes;
    private final GeneratePublicVolumeUploadUrlUseCase generateUploadUrl;
    private final FinalizePublicVolumeUseCase finalizeVolume;
    private final DeletePublicVolumeUseCase deleteVolume;

    public VolumeController(ListPublicVolumesUseCase listPublicVolumes,
                           GeneratePublicVolumeUploadUrlUseCase generateUploadUrl,
                           FinalizePublicVolumeUseCase finalizeVolume,
                           DeletePublicVolumeUseCase deleteVolume) {
        this.listPublicVolumes = listPublicVolumes;
        this.generateUploadUrl = generateUploadUrl;
        this.finalizeVolume = finalizeVolume;
        this.deleteVolume = deleteVolume;
    }

    @GetMapping
    public ResponseEntity<List<VolumeResponse>> list(@PathVariable UUID mangaId) {
        return ResponseEntity.ok(listPublicVolumes.handle(mangaId));
    }

    @PostMapping("/upload-url")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<VolumeUploadUrlResponse> getUploadUrl(
            @PathVariable UUID mangaId,
            @Valid @RequestBody VolumeUploadUrlRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(generateUploadUrl.handle(
                mangaId, request.volumeNumber(), user.getId(), user.getRole() == Role.ADMIN));
    }

    @PostMapping("/finalize")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<VolumeResponse> finalize(
            @PathVariable UUID mangaId,
            @Valid @RequestBody VolumeFinalizeRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(finalizeVolume.handle(
                        mangaId, request, user.getId(), user.getRole() == Role.ADMIN));
    }

    @DeleteMapping("/{volumeId}")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID mangaId,
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User currentUser
    ) {
        deleteVolume.handle(mangaId, volumeId, currentUser.getId(), currentUser.getRole() == Role.ADMIN);
        return ResponseEntity.noContent().build();
    }
}
