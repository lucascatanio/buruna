package com.buruna.manga.application;

import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.persistence.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FindPublicMangaUseCase {

    private final MangaRepository mangaRepository;

    public FindPublicMangaUseCase(MangaRepository mangaRepository) {
        this.mangaRepository = mangaRepository;
    }

    @Transactional(readOnly = true)
    public MangaInfo getPublicMangaInfo(UUID mangaId) {
        return mangaRepository.findById(mangaId)
                .filter(m -> m.isPublic())
                .map(m -> new MangaInfo(m.getId(), m.getSlug(), m.getTitle(), m.getCoverUrl()))
                .orElseThrow(() -> new MangaNotFoundException(mangaId));
    }

    @Transactional(readOnly = true)
    public void requirePublicManga(UUID mangaId) {
        getPublicMangaInfo(mangaId);
    }
}
