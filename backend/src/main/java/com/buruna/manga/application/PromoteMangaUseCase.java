package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.PublicTitleConflictException;
import com.buruna.manga.domain.PublicVolumeConflictException;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.persistence.MangaRepository;
import com.buruna.manga.persistence.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * COLLABORATOR/ADMIN promove o próprio mangá privado direto para o catálogo público. RBAC
 * fica na borda (@PreAuthorize); posse por {@code actorId} (ADR-35). Preserva a validação de
 * conflito (título/hash) contra a biblioteca pública, agora com exceções de domínio puras
 * (ADR-33) em vez de HttpStatus na application. O slug não é revalidado: ele já é único na
 * tabela inteira (privados e públicos), então o mangá promovido nunca conflita com outro.
 */
@Service
public class PromoteMangaUseCase {

    private final MangaRepository mangaRepository;
    private final VolumeRepository volumeRepository;
    private final PrivateMangaAccess access;
    private final PrivateMangaMapper mapper;

    public PromoteMangaUseCase(MangaRepository mangaRepository,
                               VolumeRepository volumeRepository,
                               PrivateMangaAccess access,
                               PrivateMangaMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.volumeRepository = volumeRepository;
        this.access = access;
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

        manga.promoteToPublic();
        return mapper.toResponse(mangaRepository.save(manga));
    }
}
