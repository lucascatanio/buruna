package com.buruna.engagement.web;

import com.buruna.engagement.application.RatingRequest;
import com.buruna.engagement.application.RatingResponse;
import com.buruna.engagement.application.RatingService;
import com.buruna.identity.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
// /mangas: rota de antes do rename (ADR-45), mantida por uma versão para abas abertas com o
// front antigo. Remover na versão seguinte.
@RequestMapping({"/works/{workId}/rating", "/mangas/{workId}/rating"})
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping
    public ResponseEntity<RatingResponse> getMyRating(
            @PathVariable UUID workId,
            @AuthenticationPrincipal User user) {
        return ratingService.findByUser(workId, user.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<RatingResponse> rate(
            @PathVariable UUID workId,
            @Valid @RequestBody RatingRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ratingService.rate(workId, request, user.getId()));
    }

    @PutMapping
    public ResponseEntity<RatingResponse> update(
            @PathVariable UUID workId,
            @Valid @RequestBody RatingRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ratingService.update(workId, request, user.getId()));
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(
            @PathVariable UUID workId,
            @AuthenticationPrincipal User user) {
        ratingService.remove(workId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
