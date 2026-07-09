package com.buruna.manga.application;

import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.persistence.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetMangaInfoUseCase {

    private final MangaRepository mangaRepository;

    public GetMangaInfoUseCase(MangaRepository mangaRepository) {
        this.mangaRepository = mangaRepository;
    }

    @Transactional(readOnly = true)
    public Map<UUID, MangaInfo> getInfoByIds(Collection<UUID> mangaIds) {
        if (mangaIds.isEmpty()) return Map.of();
        return mangaRepository.findAllById(mangaIds).stream()
                .collect(Collectors.toMap(
                        m -> m.getId(),
                        m -> new MangaInfo(m.getId(), m.getSlug(), m.getTitle(), m.getCoverUrl())
                ));
    }

    /** Lança MangaNotFoundException se o mangá não existir — usado pelo contexto reading. */
    @Transactional(readOnly = true)
    public void requireExists(UUID mangaId) {
        if (!mangaRepository.existsById(mangaId)) {
            throw new MangaNotFoundException(mangaId);
        }
    }
}
