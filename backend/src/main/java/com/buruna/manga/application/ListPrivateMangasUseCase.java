package com.buruna.manga.application;

import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.repository.MangaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Lista, paginada, a coleção privada do ator. */
@Service
public class ListPrivateMangasUseCase {

    private final MangaRepository mangaRepository;
    private final PrivateMangaMapper mapper;

    public ListPrivateMangasUseCase(MangaRepository mangaRepository, PrivateMangaMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<PrivateMangaResponse> handle(UUID actorId, Pageable pageable) {
        return mangaRepository.findAllByOwnerIdAndIsPublicFalse(actorId, pageable)
                .map(mapper::toResponse);
    }
}
