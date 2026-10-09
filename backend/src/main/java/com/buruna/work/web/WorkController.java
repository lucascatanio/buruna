package com.buruna.work.web;

import com.buruna.work.application.CatalogQueryUseCase;
import com.buruna.work.application.CreatePublicWorkUseCase;
import com.buruna.work.application.DeleteWorkUseCase;
import com.buruna.work.application.GetWorkUseCase;
import com.buruna.work.application.UpdateWorkUseCase;
import com.buruna.work.domain.WorkFormat;
import com.buruna.work.domain.WorkStatusOrigin;
import com.buruna.work.application.WorkRequest;
import com.buruna.work.application.WorkResponse;
import com.buruna.identity.domain.Role;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

@RestController
// /mangas: rota de antes do rename (ADR-45), mantida por uma versão para abas abertas com o
// front antigo. Remover na versão seguinte.
@RequestMapping({"/works", "/mangas"})
public class WorkController {

    // sem allowlist o Pageable ordena por qualquer atributo da entidade, inclusive os de
    // moderação fora do DTO (ex.: reviewedAt, ownerId), e vaza a ordem relativa deles
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("title", "createdAt", "updatedAt", "avgRating", "viewCount", "year");

    private final CatalogQueryUseCase catalogQuery;
    private final GetWorkUseCase getWork;
    private final CreatePublicWorkUseCase createPublicWork;
    private final UpdateWorkUseCase updateWork;
    private final DeleteWorkUseCase deleteWork;

    public WorkController(CatalogQueryUseCase catalogQuery,
                          GetWorkUseCase getWork,
                          CreatePublicWorkUseCase createPublicWork,
                          UpdateWorkUseCase updateWork,
                          DeleteWorkUseCase deleteWork) {
        this.catalogQuery = catalogQuery;
        this.getWork = getWork;
        this.createPublicWork = createPublicWork;
        this.updateWork = updateWork;
        this.deleteWork = deleteWork;
    }

    @GetMapping
    public ResponseEntity<Page<WorkResponse>> listPublic(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) WorkFormat format,
            @RequestParam(required = false) WorkStatusOrigin statusOrigin,
            @RequestParam(required = false) Set<UUID> tagIds,
            @PageableDefault(size = 20, sort = "title") Pageable pageable
    ) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Campo de ordenação inválido: " + order.getProperty());
            }
        });
        return ResponseEntity.ok(catalogQuery.handle(title, format, statusOrigin, tagIds, pageable));
    }

    @GetMapping("/{slugOrId}")
    public ResponseEntity<WorkResponse> getBySlugOrId(@PathVariable String slugOrId) {
        return ResponseEntity.ok(getWork.handle(slugOrId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<WorkResponse> create(
            @Valid @RequestBody WorkRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createPublicWork.handle(request, currentUser.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<WorkResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody WorkRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(updateWork.handle(
                id, request, currentUser.getId(), currentUser.getRole() == Role.ADMIN));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        deleteWork.handle(id, currentUser.getId(), currentUser.getRole() == Role.ADMIN);
        return ResponseEntity.noContent().build();
    }
}
