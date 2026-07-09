package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.PublicTitleConflictException;
import com.buruna.manga.domain.PublicVolumeConflictException;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.persistence.MangaRepository;
import com.buruna.manga.persistence.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * COLLABORATOR/ADMIN promove o próprio mangá privado direto para o catálogo público. RBAC
 * fica na borda (@PreAuthorize); posse por {@code actorId} (ADR-35). Preserva a validação de
 * conflito (título/hash/slug) contra a biblioteca pública, agora com exceções de domínio
 * puras (ADR-33) em vez de HttpStatus na application.
 */
@Service
public class PromoteMangaUseCase {

    private final MangaRepository mangaRepository;
    private final VolumeRepository volumeRepository;
    private final PrivateMangaAccess access;
    private final SlugAllocator slugAllocator;
    private final PrivateMangaMapper mapper;

    public PromoteMangaUseCase(MangaRepository mangaRepository,
                               VolumeRepository volumeRepository,
                               PrivateMangaAccess access,
                               SlugAllocator slugAllocator,
                               PrivateMangaMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.volumeRepository = volumeRepository;
        this.access = access;
        this.slugAllocator = slugAllocator;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateMangaResponse handle(UUID mangaId, UUID actorId) {
        Manga manga = access.findOwned(mangaId, actorId);

        // 1. título duplicado na biblioteca pública
        if (mangaRepository.existsByTitleIgnoreCaseAndIsPublicTrue(manga.getTitle())) {
            throw new PublicTitleConflictException(manga.getTitle());
        }

        // 2. hash de volume duplicado em mangá público
        List<Volume> volumes = volumeRepository.findByMangaId(mangaId);
        boolean hasPublicHash = volumes.stream()
                .anyMatch(v -> volumeRepository.existsByFileHashAndMangaIsPublicTrue(v.getFileHash()));
        if (hasPublicHash) {
            throw new PublicVolumeConflictException();
        }

        // 3. slug em conflito: regenera se necessário
        if (mangaRepository.existsBySlug(manga.getSlug())) {
            manga.changeSlug(slugAllocator.allocate(manga.getTitle()));
        }

        manga.promoteToPublic();
        return mapper.toResponse(mangaRepository.save(manga));
    }
}
