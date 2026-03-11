package com.buruna.manga.controller;

import com.buruna.manga.dto.PrivateMangaRequest;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.dto.QuotaInfo;
import com.buruna.manga.service.PrivateMangaService;
import com.buruna.user.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/my/mangas")
public class PrivateMangaController {

    private final PrivateMangaService privateMangaService;

    public PrivateMangaController(PrivateMangaService privateMangaService) {
        this.privateMangaService = privateMangaService;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PrivateMangaResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") @NotBlank String title,
            @RequestParam("volumeNumber") @NotNull Integer volumeNumber,
            @RequestParam(value = "synopsis", required = false) String synopsis,
            @RequestParam(value = "coverBase64", required = false) String coverBase64,
            @AuthenticationPrincipal User currentUser
    ) {
        PrivateMangaResponse response = privateMangaService.upload(
                title, synopsis, coverBase64, volumeNumber, file, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

    @PostMapping(value = "/{id}/volumes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PrivateMangaResponse> addVolume(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("volumeNumber") @NotNull Integer volumeNumber,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(privateMangaService.addVolume(id, volumeNumber, file, currentUser));
    }

    @DeleteMapping("/{id}/volumes/{volumeId}")
    public ResponseEntity<PrivateMangaResponse> deleteVolume(
            @PathVariable UUID id,
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(privateMangaService.deleteVolume(id, volumeId, currentUser));
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<PrivateMangaResponse> promote(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(privateMangaService.promote(id, currentUser));
    }
}
