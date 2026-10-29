package com.buruna.work.web;

import com.buruna.work.application.CreatePrivateWorkUseCase;
import com.buruna.work.application.DeletePrivateWorkUseCase;
import com.buruna.work.application.DeleteVolumeUseCase;
import com.buruna.work.application.FinalizeVolumeUseCase;
import com.buruna.work.application.GenerateVolumeUploadUrlUseCase;
import com.buruna.work.application.GetPrivateWorkUseCase;
import com.buruna.work.application.ListPrivateWorksUseCase;
import com.buruna.work.application.PromoteWorkUseCase;
import com.buruna.work.application.QuotaService;
import com.buruna.work.application.SubmitForApprovalUseCase;
import com.buruna.work.application.UpdatePrivateWorkUseCase;
import com.buruna.work.application.PrivateWorkRequest;
import com.buruna.work.application.PrivateWorkResponse;
import com.buruna.work.application.QuotaInfo;
import com.buruna.work.application.VolumeFinalizeRequest;
import com.buruna.work.application.VolumeUploadUrlResponse;
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
// /mangas: rota de antes do rename (ADR-45), mantida por uma versão para abas abertas com o
// front antigo. Remover na versão seguinte.
@RequestMapping({"/my/works", "/my/mangas"})
public class PrivateWorkController {

    private final CreatePrivateWorkUseCase createPrivateWork;
    private final UpdatePrivateWorkUseCase updatePrivateWork;
    private final DeletePrivateWorkUseCase deletePrivateWork;
    private final GenerateVolumeUploadUrlUseCase generateVolumeUploadUrl;
    private final FinalizeVolumeUseCase finalizeVolume;
    private final DeleteVolumeUseCase deleteVolume;
    private final GetPrivateWorkUseCase getPrivateWork;
    private final ListPrivateWorksUseCase listPrivateWorks;
    private final QuotaService quotaService;
    private final SubmitForApprovalUseCase submitForApproval;
    private final PromoteWorkUseCase promoteWork;

    public PrivateWorkController(CreatePrivateWorkUseCase createPrivateWork,
                                  UpdatePrivateWorkUseCase updatePrivateWork,
                                  DeletePrivateWorkUseCase deletePrivateWork,
                                  GenerateVolumeUploadUrlUseCase generateVolumeUploadUrl,
                                  FinalizeVolumeUseCase finalizeVolume,
                                  DeleteVolumeUseCase deleteVolume,
                                  GetPrivateWorkUseCase getPrivateWork,
                                  ListPrivateWorksUseCase listPrivateWorks,
                                  QuotaService quotaService,
                                  SubmitForApprovalUseCase submitForApproval,
                                  PromoteWorkUseCase promoteWork) {
        this.createPrivateWork = createPrivateWork;
        this.updatePrivateWork = updatePrivateWork;
        this.deletePrivateWork = deletePrivateWork;
        this.generateVolumeUploadUrl = generateVolumeUploadUrl;
        this.finalizeVolume = finalizeVolume;
        this.deleteVolume = deleteVolume;
        this.getPrivateWork = getPrivateWork;
        this.listPrivateWorks = listPrivateWorks;
        this.quotaService = quotaService;
        this.submitForApproval = submitForApproval;
        this.promoteWork = promoteWork;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrivateWorkResponse> findById(@PathVariable UUID id,
                                                         @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(getPrivateWork.handle(id, user.getId()));
    }

    @GetMapping
    public ResponseEntity<Page<PrivateWorkResponse>> listMine(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(listPrivateWorks.handle(currentUser.getId(), pageable));
    }

    @GetMapping("/quota")
    public ResponseEntity<QuotaInfo> getQuota(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(quotaService.getQuotaInfo(currentUser.getId(), currentUser.getQuotaGb()));
    }

    @PostMapping
    public ResponseEntity<PrivateWorkResponse> create(
            @Valid @RequestBody PrivateWorkCreateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        PrivateWorkResponse response = createPrivateWork.handle(
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
    public ResponseEntity<PrivateWorkResponse> finalizeVolume(
            @PathVariable UUID id,
            @Valid @RequestBody VolumeFinalizeRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(finalizeVolume.handle(id, request, currentUser.getId(), currentUser.getQuotaGb()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrivateWorkResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PrivateWorkRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(updatePrivateWork.handle(id, request, currentUser.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        deletePrivateWork.handle(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/volumes/{volumeId}")
    public ResponseEntity<PrivateWorkResponse> deleteVolume(
            @PathVariable UUID id,
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(deleteVolume.handle(id, volumeId, currentUser.getId()));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<PrivateWorkResponse> submitForApproval(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                submitForApproval.handle(id, currentUser.getId(), currentUser.getUsername()));
    }

    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    @PostMapping("/{id}/promote")
    public ResponseEntity<PrivateWorkResponse> promote(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(promoteWork.handle(id, currentUser.getId()));
    }
}
