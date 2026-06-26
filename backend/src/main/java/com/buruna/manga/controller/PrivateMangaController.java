package com.buruna.manga.controller;

import com.buruna.manga.application.CreatePrivateMangaUseCase;
import com.buruna.manga.application.DeletePrivateMangaUseCase;
import com.buruna.manga.application.DeleteVolumeUseCase;
import com.buruna.manga.application.FinalizeVolumeUseCase;
import com.buruna.manga.application.GenerateVolumeUploadUrlUseCase;
import com.buruna.manga.application.GetPrivateMangaUseCase;
import com.buruna.manga.application.ListPrivateMangasUseCase;
import com.buruna.manga.application.PromoteMangaUseCase;
import com.buruna.manga.application.QuotaService;
import com.buruna.manga.application.SubmitForApprovalUseCase;
import com.buruna.manga.application.UpdatePrivateMangaUseCase;
import com.buruna.manga.dto.*;
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

    private final CreatePrivateMangaUseCase createPrivateManga;
    private final UpdatePrivateMangaUseCase updatePrivateManga;
    private final DeletePrivateMangaUseCase deletePrivateManga;
    private final GenerateVolumeUploadUrlUseCase generateVolumeUploadUrl;
    private final FinalizeVolumeUseCase finalizeVolume;
    private final DeleteVolumeUseCase deleteVolume;
    private final GetPrivateMangaUseCase getPrivateManga;
    private final ListPrivateMangasUseCase listPrivateMangas;
    private final QuotaService quotaService;
    private final SubmitForApprovalUseCase submitForApproval;
    private final PromoteMangaUseCase promoteManga;

    public PrivateMangaController(CreatePrivateMangaUseCase createPrivateManga,
                                  UpdatePrivateMangaUseCase updatePrivateManga,
                                  DeletePrivateMangaUseCase deletePrivateManga,
                                  GenerateVolumeUploadUrlUseCase generateVolumeUploadUrl,
                                  FinalizeVolumeUseCase finalizeVolume,
                                  DeleteVolumeUseCase deleteVolume,
                                  GetPrivateMangaUseCase getPrivateManga,
                                  ListPrivateMangasUseCase listPrivateMangas,
                                  QuotaService quotaService,
                                  SubmitForApprovalUseCase submitForApproval,
                                  PromoteMangaUseCase promoteManga) {
        this.createPrivateManga = createPrivateManga;
        this.updatePrivateManga = updatePrivateManga;
        this.deletePrivateManga = deletePrivateManga;
        this.generateVolumeUploadUrl = generateVolumeUploadUrl;
        this.finalizeVolume = finalizeVolume;
        this.deleteVolume = deleteVolume;
        this.getPrivateManga = getPrivateManga;
        this.listPrivateMangas = listPrivateMangas;
        this.quotaService = quotaService;
        this.submitForApproval = submitForApproval;
        this.promoteManga = promoteManga;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrivateMangaResponse> findById(@PathVariable UUID id,
                                                         @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(getPrivateManga.handle(id, user.getId()));
    }

    @GetMapping
    public ResponseEntity<Page<PrivateMangaResponse>> listMine(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(listPrivateMangas.handle(currentUser.getId(), pageable));
    }

    @GetMapping("/quota")
    public ResponseEntity<QuotaInfo> getQuota(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(quotaService.getQuotaInfo(currentUser.getId(), currentUser.getQuotaGb()));
    }

    @PostMapping
    public ResponseEntity<PrivateMangaResponse> create(
            @Valid @RequestBody PrivateMangaCreateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        PrivateMangaResponse response = createPrivateManga.handle(
                request.title(), request.synopsis(), request.coverBase64(), currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/volumes/upload-url")
    public ResponseEntity<VolumeUploadUrlResponse> getUploadUrl(
            @PathVariable UUID id,
            @Valid @RequestBody VolumeUploadUrlRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                generateVolumeUploadUrl.handle(id, request.volumeNumber(), currentUser.getId()));
    }

    @PostMapping("/{id}/volumes/finalize")
    public ResponseEntity<PrivateMangaResponse> finalizeVolume(
            @PathVariable UUID id,
            @Valid @RequestBody VolumeFinalizeRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(finalizeVolume.handle(id, request, currentUser.getId(), currentUser.getQuotaGb()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrivateMangaResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PrivateMangaRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(updatePrivateManga.handle(id, request, currentUser.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        deletePrivateManga.handle(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/volumes/{volumeId}")
    public ResponseEntity<PrivateMangaResponse> deleteVolume(
            @PathVariable UUID id,
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(deleteVolume.handle(id, volumeId, currentUser.getId()));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<PrivateMangaResponse> submitForApproval(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                submitForApproval.handle(id, currentUser.getId(), currentUser.getUsername()));
    }

    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    @PostMapping("/{id}/promote")
    public ResponseEntity<PrivateMangaResponse> promote(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(promoteManga.handle(id, currentUser.getId()));
    }
}
