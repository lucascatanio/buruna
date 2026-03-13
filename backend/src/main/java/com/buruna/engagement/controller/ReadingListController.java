package com.buruna.engagement.controller;

import com.buruna.engagement.dto.ReadingListRequest;
import com.buruna.engagement.dto.ReadingListResponse;
import com.buruna.engagement.service.ReadingListService;
import com.buruna.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reading-list")
public class ReadingListController {

    private final ReadingListService readingListService;

    public ReadingListController(ReadingListService readingListService) {
        this.readingListService = readingListService;
    }

    @GetMapping
    public ResponseEntity<List<ReadingListResponse>> findAll(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readingListService.findAll(user));
    }

    @PutMapping("/{mangaId}")
    public ResponseEntity<ReadingListResponse> upsert(
            @PathVariable UUID mangaId,
            @Valid @RequestBody ReadingListRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readingListService.upsert(mangaId, request, user));
    }

    @DeleteMapping("/{mangaId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID mangaId,
            @AuthenticationPrincipal User user) {
        readingListService.remove(mangaId, user);
        return ResponseEntity.noContent().build();
    }
}