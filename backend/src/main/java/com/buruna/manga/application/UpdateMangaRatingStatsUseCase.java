package com.buruna.manga.application;

import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.persistence.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class UpdateMangaRatingStatsUseCase {

    private final MangaRepository mangaRepository;

    public UpdateMangaRatingStatsUseCase(MangaRepository mangaRepository) {
        this.mangaRepository = mangaRepository;
    }

    @Transactional
    public void handle(UUID mangaId, BigDecimal avgRating, int ratingCount) {
        var manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));
        manga.applyRatingStats(avgRating, ratingCount);
        mangaRepository.save(manga);
    }
}
