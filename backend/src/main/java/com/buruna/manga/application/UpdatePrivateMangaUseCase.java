package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.PrivateMangaRequest;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.persistence.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Atualiza título/sinopse de um mangá da coleção privada do ator. */
@Service
public class UpdatePrivateMangaUseCase {

    private final MangaRepository mangaRepository;
    private final PrivateMangaAccess access;
    private final PrivateMangaMapper mapper;

    public UpdatePrivateMangaUseCase(MangaRepository mangaRepository,
                                     PrivateMangaAccess access,
                                     PrivateMangaMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateMangaResponse handle(UUID id, PrivateMangaRequest request, UUID actorId) {
        Manga manga = access.findOwned(id, actorId);
        manga.updatePrivateDetails(request.title(), request.synopsis());
        return mapper.toResponse(mangaRepository.save(manga));
    }
}
