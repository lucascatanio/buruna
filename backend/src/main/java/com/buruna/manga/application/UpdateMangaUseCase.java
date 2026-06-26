package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.MangaRequest;
import com.buruna.manga.dto.MangaResponse;
import com.buruna.manga.repository.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Atualiza um mangá do catálogo. Posse "dono OU ADMIN" (ADR-35) via PublicMangaAccess. */
@Service
public class UpdateMangaUseCase {

    private final MangaRepository mangaRepository;
    private final PublicMangaAccess access;
    private final MangaRequestApplier requestApplier;
    private final MangaResponseMapper mapper;

    public UpdateMangaUseCase(MangaRepository mangaRepository,
                              PublicMangaAccess access,
                              MangaRequestApplier requestApplier,
                              MangaResponseMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.access = access;
        this.requestApplier = requestApplier;
        this.mapper = mapper;
    }

    @Transactional
    public MangaResponse handle(UUID id, MangaRequest request, UUID actorId, boolean isAdmin) {
        Manga manga = access.findModifiable(id, actorId, isAdmin);
        requestApplier.apply(manga, request);
        return mapper.toResponse(mangaRepository.save(manga), true);
    }
}
