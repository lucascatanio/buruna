package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.exception.PrivateMangaAccessDeniedException;
import com.buruna.manga.repository.MangaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Carrega um mangá da coleção privada garantindo posse (ownership) por {@code actorId},
 * concentrando a regra antes espalhada em PrivateMangaService (ADR-35). RBAC fica na
 * borda (@PreAuthorize); aqui só posse. Um mangá público é tratado como inexistente na
 * coleção privada (404), preservando o comportamento atual.
 */
@Component
public class PrivateMangaAccess {

    private final MangaRepository mangaRepository;

    public PrivateMangaAccess(MangaRepository mangaRepository) {
        this.mangaRepository = mangaRepository;
    }

    public Manga findOwned(UUID mangaId, UUID actorId) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));
        if (manga.isPublic()) {
            throw new MangaNotFoundException(mangaId);
        }
        if (!manga.getOwnerId().equals(actorId)) {
            throw new PrivateMangaAccessDeniedException();
        }
        return manga;
    }
}
