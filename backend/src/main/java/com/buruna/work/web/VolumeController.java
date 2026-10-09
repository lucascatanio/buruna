package com.buruna.work.web;

import com.buruna.work.application.DeletePublicVolumeUseCase;
import com.buruna.work.application.FinalizePublicVolumeUseCase;
import com.buruna.work.application.GeneratePublicVolumeUploadUrlUseCase;
import com.buruna.work.application.ListPublicVolumesUseCase;
import com.buruna.work.application.VolumeFinalizeRequest;
import com.buruna.work.application.VolumeResponse;
import com.buruna.work.application.VolumeUploadUrlResponse;
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
// /mangas: rota de antes do rename (ADR-45), mantida por uma versão para abas abertas com o
// front antigo. Remover na versão seguinte.
@RequestMapping({"/works/{workId}/volumes", "/mangas/{workId}/volumes"})
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
    public ResponseEntity<List<VolumeResponse>> list(@PathVariable UUID workId) {
        return ResponseEntity.ok(listPublicVolumes.handle(workId));
    }

    @PostMapping("/upload-url")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<VolumeUploadUrlResponse> getUploadUrl(
            @PathVariable UUID workId,
            @Valid @RequestBody VolumeUploadUrlRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(generateUploadUrl.handle(
                workId, request.volumeNumber(), user.getId(), user.getRole() == Role.ADMIN));
    }

    @PostMapping("/finalize")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<VolumeResponse> finalize(
            @PathVariable UUID workId,
            @Valid @RequestBody VolumeFinalizeRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(finalizeVolume.handle(
                        workId, request, user.getId(), user.getRole() == Role.ADMIN));
    }

    @DeleteMapping("/{volumeId}")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID workId,
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User currentUser
    ) {
        deleteVolume.handle(workId, volumeId, currentUser.getId(), currentUser.getRole() == Role.ADMIN);
        return ResponseEntity.noContent().build();
    }
}
