package com.buruna.reader.controller;

import com.buruna.reader.dto.HistoryResponse;
import com.buruna.reader.dto.ProgressRequest;
import com.buruna.reader.dto.ProgressResponse;
import com.buruna.reader.dto.VolumeUrlResponse;
import com.buruna.reader.service.ReaderService;
import com.buruna.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reader")
public class ReaderController {

    private final ReaderService readerService;

    public ReaderController(ReaderService readerService) {
        this.readerService = readerService;
    }

    @GetMapping("/{volumeId}/url")
    public ResponseEntity<VolumeUrlResponse> getVolumeUrl(
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readerService.getVolumeUrl(volumeId, user));
    }

    @PostMapping("/{volumeId}/progress")
    public ResponseEntity<ProgressResponse> saveProgress(
            @PathVariable UUID volumeId,
            @Valid @RequestBody ProgressRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readerService.saveProgress(volumeId, request, user));
    }

    @GetMapping("/progress/{mangaId}")
    public ResponseEntity<ProgressResponse> getProgress(
            @PathVariable UUID mangaId,
            @AuthenticationPrincipal User user) {
        ProgressResponse response = readerService.getProgress(mangaId, user);
        // 204 quando nunca leu — frontend trata como início do vol. 1 pág. 1
        return response != null
                ? ResponseEntity.ok(response)
                : ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    public ResponseEntity<Page<HistoryResponse>> getHistory(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(readerService.getHistory(user, pageable));
    }

    @GetMapping("/progress/batch")
    public ResponseEntity<Map<UUID, Integer>> getBatchProgress(
            @RequestParam List<UUID> volumeIds,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readerService.getBatchProgress(volumeIds, user));
    }

    @GetMapping("/{volumeId}/progress")
    public ResponseEntity<ProgressResponse> getVolumeProgress(
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User user) {
        return readerService.findProgressByVolume(volumeId, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}