package com.buruna.engagement.application;

import com.buruna.engagement.domain.MangaNotFoundException;
import com.buruna.engagement.domain.Rating;
import com.buruna.engagement.domain.RatingAlreadyExistsException;
import com.buruna.engagement.domain.RatingNotFoundException;
import com.buruna.engagement.domain.Score;
import com.buruna.engagement.persistence.RatingRepository;
import com.buruna.engagement.web.RatingRequest;
import com.buruna.engagement.web.RatingResponse;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.repository.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    public RatingResponse rate(UUID mangaId, RatingRequest request, UUID actorId) {
        Manga manga = findPublicManga(mangaId);

        if (ratingRepository.findByUserIdAndMangaId(actorId, mangaId).isPresent()) {
            throw new RatingAlreadyExistsException(mangaId);
        }

        Score score = Score.of(request.score());
        ratingRepository.save(Rating.create(actorId, mangaId, score));

        return toResponse(mangaId, score.value(), recalculate(manga));
    }

    @Transactional
    public RatingResponse update(UUID mangaId, RatingRequest request, UUID actorId) {
        Rating rating = ratingRepository.findByUserIdAndMangaId(actorId, mangaId)
                .orElseThrow(() -> new RatingNotFoundException(mangaId));

        Score score = Score.of(request.score());
        rating.updateScore(score);
        ratingRepository.save(rating);

        Manga manga = findPublicManga(mangaId);
        return toResponse(mangaId, score.value(), recalculate(manga));
    }

    @Transactional
    public void remove(UUID mangaId, UUID actorId) {
        if (ratingRepository.findByUserIdAndMangaId(actorId, mangaId).isEmpty()) {
            throw new RatingNotFoundException(mangaId);
        }
        ratingRepository.deleteByUserIdAndMangaId(actorId, mangaId);
        recalculate(findPublicManga(mangaId));
    }

    @Transactional(readOnly = true)
    public Optional<RatingResponse> findByUser(UUID mangaId, UUID actorId) {
        return ratingRepository.findByUserIdAndMangaId(actorId, mangaId)
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
        manga.setAvgRating(BigDecimal.valueOf(avgRounded));
        manga.setRatingCount(count);
        mangaRepository.save(manga);
        return new RecalcResult(BigDecimal.valueOf(avgRounded), count);
    }

    private Manga findPublicManga(UUID mangaId) {
        return mangaRepository.findById(mangaId)
                .filter(Manga::isPublic)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));
    }

    private RatingResponse toResponse(UUID mangaId, int score, RecalcResult recalc) {
        return new RatingResponse(mangaId, score, recalc.avg(), recalc.count());
    }

    private record RecalcResult(BigDecimal avg, int count) {}
}
