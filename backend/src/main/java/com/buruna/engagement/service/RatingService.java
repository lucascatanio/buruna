package com.buruna.engagement.service;

import com.buruna.engagement.domain.Rating;
import com.buruna.engagement.dto.RatingRequest;
import com.buruna.engagement.dto.RatingResponse;
import com.buruna.engagement.repository.RatingRepository;
import com.buruna.shared.exception.LegacyHttpDomainException;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.user.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final MangaRepository mangaRepository;

    public RatingService(RatingRepository ratingRepository,
                         MangaRepository mangaRepository) {
        this.ratingRepository = ratingRepository;
        this.mangaRepository = mangaRepository;
    }

    @Transactional
    public RatingResponse rate(UUID mangaId, RatingRequest request, User user) {
        Manga manga = findPublicManga(mangaId);

        if (ratingRepository.findByUserIdAndMangaId(user.getId(), mangaId).isPresent()) {
            throw new LegacyHttpDomainException(HttpStatus.CONFLICT,
                    "Você já avaliou este mangá. Use PUT para atualizar.");
        }

        Rating rating = new Rating();
        rating.setUser(user);
        rating.setManga(manga);
        rating.setScore(request.score());
        Rating saved = ratingRepository.save(rating);

        return toResponse(mangaId, saved.getScore(), recalculate(manga));
    }

    @Transactional
    public RatingResponse update(UUID mangaId, RatingRequest request, User user) {
        Rating rating = ratingRepository.findByUserIdAndMangaId(user.getId(), mangaId)
                .orElseThrow(() -> new LegacyHttpDomainException(HttpStatus.NOT_FOUND,
                        "Você ainda não avaliou este mangá. Use POST para avaliar."));

        rating.setScore(request.score());
        ratingRepository.save(rating);

        Manga manga = findPublicManga(mangaId);
        return toResponse(mangaId, rating.getScore(), recalculate(manga));
    }

    @Transactional
    public void remove(UUID mangaId, User user) {
        if (ratingRepository.findByUserIdAndMangaId(user.getId(), mangaId).isEmpty()) {
            throw new LegacyHttpDomainException(HttpStatus.NOT_FOUND, "Avaliação não encontrada");
        }
        ratingRepository.deleteByUserIdAndMangaId(user.getId(), mangaId);
        recalculate(findPublicManga(mangaId));
    }

    @Transactional(readOnly = true)
    public Optional<RatingResponse> findByUser(UUID mangaId, User user) {
        return ratingRepository.findByUserIdAndMangaId(user.getId(), mangaId)
                .map(r -> {
                    Manga manga = findPublicManga(mangaId);
                    return new RatingResponse(
                            mangaId,
                            r.getScore(),
                            manga.getAvgRating(),
                            manga.getRatingCount()
                    );
                });
    }

    private RecalcResult recalculate(Manga manga) {
        double avg = ratingRepository.avgScoreByMangaId(manga.getId());
        int count = ratingRepository.countByMangaId(manga.getId());

        double avgRounded = Math.round(avg * 10.0) / 10.0;

        manga.setAvgRating(java.math.BigDecimal.valueOf(avgRounded));
        manga.setRatingCount(count);
        mangaRepository.save(manga);

        return new RecalcResult(java.math.BigDecimal.valueOf(avgRounded), count);
    }

    private Manga findPublicManga(UUID mangaId) {
        return mangaRepository.findById(mangaId)
                .filter(Manga::isPublic)
                .orElseThrow(() -> new LegacyHttpDomainException(HttpStatus.NOT_FOUND, "Mangá não encontrado"));
    }

    private RatingResponse toResponse(UUID mangaId, int score, RecalcResult recalc) {
        return new RatingResponse(mangaId, score, recalc.avg(), recalc.count());
    }

    private record RecalcResult(java.math.BigDecimal avg, int count) {}
}