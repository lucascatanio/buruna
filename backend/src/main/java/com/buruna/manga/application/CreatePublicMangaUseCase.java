package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.MangaRequest;
import com.buruna.manga.dto.MangaResponse;
import com.buruna.manga.exception.MangaAlreadyExistsException;
import com.buruna.manga.persistence.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Cria um mangá já público no catálogo. RBAC na borda; dono = ator (ADR-35). */
@Service
public class CreatePublicMangaUseCase {

    private final MangaRepository mangaRepository;
    private final SlugAllocator slugAllocator;
    private final MangaRequestApplier requestApplier;
    private final MangaResponseMapper mapper;

    public CreatePublicMangaUseCase(MangaRepository mangaRepository,
                                    SlugAllocator slugAllocator,
                                    MangaRequestApplier requestApplier,
                                    MangaResponseMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.slugAllocator = slugAllocator;
        this.requestApplier = requestApplier;
        this.mapper = mapper;
    }

    @Transactional
    public MangaResponse handle(MangaRequest request, UUID actorId) {
        if (mangaRepository.existsByTitleIgnoreCaseAndIsPublicTrue(request.title())) {
            throw new MangaAlreadyExistsException(request.title());
        }

        Manga manga = Manga.createPublic(slugAllocator.allocate(request.title()), actorId);
        requestApplier.apply(manga, request);

        return mapper.toResponse(mangaRepository.save(manga), true);
    }
}
