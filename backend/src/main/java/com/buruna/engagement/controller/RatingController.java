package com.buruna.engagement.controller;

import com.buruna.engagement.dto.RatingRequest;
import com.buruna.engagement.dto.RatingResponse;
import com.buruna.engagement.service.RatingService;
import com.buruna.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/mangas/{mangaId}/rating")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping
    public ResponseEntity<RatingResponse> getMyRating(
            @PathVariable UUID mangaId,
            @AuthenticationPrincipal User user) {
        return ratingService.findByUser(mangaId, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<RatingResponse> rate(
            @PathVariable UUID mangaId,
            @Valid @RequestBody RatingRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ratingService.rate(mangaId, request, user));
    }

    @PutMapping
    public ResponseEntity<RatingResponse> update(
            @PathVariable UUID mangaId,
            @Valid @RequestBody RatingRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ratingService.update(mangaId, request, user));
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(
            @PathVariable UUID mangaId,
            @AuthenticationPrincipal User user) {
        ratingService.remove(mangaId, user);
        return ResponseEntity.noContent().build();
    }
}