package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.exception.MangaModificationDeniedException;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.persistence.MangaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Carrega um mangá para modificação aplicando a regra "dono OU ADMIN" (ADR-35). O papel
 * chega como primitivo {@code isAdmin} da borda; a posse é por {@code actorId}. RBAC de
 * papel (COLLABORATOR/ADMIN) fica no @PreAuthorize do controller. Concentra o antigo
 * {@code assertCanModify} de MangaService/VolumeService.
 */
@Component
public class PublicMangaAccess {

    private final MangaRepository mangaRepository;

    public PublicMangaAccess(MangaRepository mangaRepository) {
        this.mangaRepository = mangaRepository;
    }

    public Manga findModifiable(UUID mangaId, UUID actorId, boolean isAdmin) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));
        if (!isAdmin && !manga.getOwnerId().equals(actorId)) {
            throw new MangaModificationDeniedException();
        }
        return manga;
    }
}
