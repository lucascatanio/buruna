package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.PrivateMangaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Lê um mangá da coleção privada do ator (com seus volumes). */
@Service
public class GetPrivateMangaUseCase {

    private final PrivateMangaAccess access;
    private final PrivateMangaMapper mapper;

    public GetPrivateMangaUseCase(PrivateMangaAccess access, PrivateMangaMapper mapper) {
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PrivateMangaResponse handle(UUID id, UUID actorId) {
        Manga manga = access.findOwned(id, actorId);
        return mapper.toResponse(manga);
    }
}
