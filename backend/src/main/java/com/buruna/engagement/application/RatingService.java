package com.buruna.engagement.application;

import com.buruna.engagement.domain.Rating;
import com.buruna.engagement.domain.RatingAlreadyExistsException;
import com.buruna.engagement.domain.RatingNotFoundException;
import com.buruna.engagement.domain.Score;
import com.buruna.engagement.persistence.RatingRepository;
import com.buruna.engagement.web.RatingRequest;
import com.buruna.engagement.web.RatingResponse;
import com.buruna.manga.application.FindPublicMangaUseCase;
import com.buruna.manga.application.UpdateMangaRatingStatsUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final FindPublicMangaUseCase findPublicMangaUseCase;
    private final UpdateMangaRatingStatsUseCase updateMangaRatingStatsUseCase;

    public RatingService(RatingRepository ratingRepository,
                         FindPublicMangaUseCase findPublicMangaUseCase,
                         UpdateMangaRatingStatsUseCase updateMangaRatingStatsUseCase) {
        this.ratingRepository = ratingRepository;
        this.findPublicMangaUseCase = findPublicMangaUseCase;
        this.updateMangaRatingStatsUseCase = updateMangaRatingStatsUseCase;
    }

    @Transactional
    public RatingResponse rate(UUID mangaId, RatingRequest request, UUID actorId) {
        findPublicMangaUseCase.requirePublicManga(mangaId);

        if (ratingRepository.findByUserIdAndMangaId(actorId, mangaId).isPresent()) {
            throw new RatingAlreadyExistsException(mangaId);
        }

        Score score = Score.of(request.score());
        ratingRepository.save(Rating.create(actorId, mangaId, score));

        RecalcResult recalc = recalcAndPush(mangaId);
        return new RatingResponse(mangaId, score.value(), recalc.avg(), recalc.count());
    }

    @Transactional
    public RatingResponse update(UUID mangaId, RatingRequest request, UUID actorId) {
        Rating rating = ratingRepository.findByUserIdAndMangaId(actorId, mangaId)
                .orElseThrow(() -> new RatingNotFoundException(mangaId));

        findPublicMangaUseCase.requirePublicManga(mangaId);

        Score score = Score.of(request.score());
        rating.updateScore(score);
        ratingRepository.save(rating);

        RecalcResult recalc = recalcAndPush(mangaId);
        return new RatingResponse(mangaId, score.value(), recalc.avg(), recalc.count());
    }

    @Transactional
    public void remove(UUID mangaId, UUID actorId) {
        if (ratingRepository.findByUserIdAndMangaId(actorId, mangaId).isEmpty()) {
            throw new RatingNotFoundException(mangaId);
        }
        ratingRepository.deleteByUserIdAndMangaId(actorId, mangaId);
        recalcAndPush(mangaId);
    }

    @Transactional(readOnly = true)
    public Optional<RatingResponse> findByUser(UUID mangaId, UUID actorId) {
        return ratingRepository.findByUserIdAndMangaId(actorId, mangaId)
                .map(r -> {
                    findPublicMangaUseCase.requirePublicManga(mangaId);
                    double avg = ratingRepository.avgScoreByMangaId(mangaId);
                    int count = ratingRepository.countByMangaId(mangaId);
                    return new RatingResponse(mangaId, r.getScore(),
                            BigDecimal.valueOf(Math.round(avg * 10.0) / 10.0), count);
                });
    }

    // engagement é dono da tabela ratings: calcula avg/count aqui e empurra para manga
    private RecalcResult recalcAndPush(UUID mangaId) {
        double avg = ratingRepository.avgScoreByMangaId(mangaId);
        int count = ratingRepository.countByMangaId(mangaId);
        BigDecimal avgRounded = BigDecimal.valueOf(Math.round(avg * 10.0) / 10.0);
        updateMangaRatingStatsUseCase.handle(mangaId, avgRounded, count);
        return new RecalcResult(avgRounded, count);
    }

    private record RecalcResult(BigDecimal avg, int count) {}
}
