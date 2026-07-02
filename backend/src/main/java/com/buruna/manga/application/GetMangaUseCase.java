package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.MangaResponse;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.persistence.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Detalhe de um mangá público resolvendo slug OU UUID no mesmo endpoint (ADR-13): tenta
 * por UUID, cai para slug, e exige que seja público. Inclui os volumes.
 */
@Service
public class GetMangaUseCase {

    private final MangaRepository mangaRepository;
    private final MangaResponseMapper mapper;

    public GetMangaUseCase(MangaRepository mangaRepository, MangaResponseMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public MangaResponse handle(String slugOrId) {
        Manga manga = parseUuid(slugOrId)
                .flatMap(mangaRepository::findById)
                .or(() -> mangaRepository.findBySlug(slugOrId))
                .filter(Manga::isPublic)
                .orElseThrow(() -> new MangaNotFoundException(slugOrId));
        return mapper.toResponse(manga, true);
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
