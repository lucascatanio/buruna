package com.buruna.engagement.application;

import com.buruna.engagement.domain.Rating;
import com.buruna.engagement.domain.RatingAlreadyExistsException;
import com.buruna.engagement.domain.RatingNotFoundException;
import com.buruna.engagement.domain.Score;
import com.buruna.engagement.persistence.RatingRepository;


import com.buruna.work.application.FindPublicWorkUseCase;
import com.buruna.work.application.UpdateWorkRatingStatsUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final FindPublicWorkUseCase findPublicWorkUseCase;
    private final UpdateWorkRatingStatsUseCase updateWorkRatingStatsUseCase;

    public RatingService(RatingRepository ratingRepository,
                         FindPublicWorkUseCase findPublicWorkUseCase,
                         UpdateWorkRatingStatsUseCase updateWorkRatingStatsUseCase) {
        this.ratingRepository = ratingRepository;
        this.findPublicWorkUseCase = findPublicWorkUseCase;
        this.updateWorkRatingStatsUseCase = updateWorkRatingStatsUseCase;
    }

    @Transactional
    public RatingResponse rate(UUID workId, RatingRequest request, UUID actorId) {
        findPublicWorkUseCase.requirePublicWork(workId);

        if (ratingRepository.findByUserIdAndWorkId(actorId, workId).isPresent()) {
            throw new RatingAlreadyExistsException(workId);
        }

        Score score = Score.of(request.score());
        ratingRepository.save(Rating.create(actorId, workId, score));

        RecalcResult recalc = recalcAndPush(workId);
        return new RatingResponse(workId, score.value(), recalc.avg(), recalc.count());
    }

    @Transactional
    public RatingResponse update(UUID workId, RatingRequest request, UUID actorId) {
        Rating rating = ratingRepository.findByUserIdAndWorkId(actorId, workId)
                .orElseThrow(() -> new RatingNotFoundException(workId));

        findPublicWorkUseCase.requirePublicWork(workId);

        Score score = Score.of(request.score());
        rating.updateScore(score);
        ratingRepository.save(rating);

        RecalcResult recalc = recalcAndPush(workId);
        return new RatingResponse(workId, score.value(), recalc.avg(), recalc.count());
    }

    @Transactional
    public void remove(UUID workId, UUID actorId) {
        if (ratingRepository.findByUserIdAndWorkId(actorId, workId).isEmpty()) {
            throw new RatingNotFoundException(workId);
        }
        ratingRepository.deleteByUserIdAndWorkId(actorId, workId);
        recalcAndPush(workId);
    }

    @Transactional(readOnly = true)
    public Optional<RatingResponse> findByUser(UUID workId, UUID actorId) {
        return ratingRepository.findByUserIdAndWorkId(actorId, workId)
                .map(r -> {
                    findPublicWorkUseCase.requirePublicWork(workId);
                    double avg = ratingRepository.avgScoreByWorkId(workId);
                    int count = ratingRepository.countByWorkId(workId);
                    return new RatingResponse(workId, r.getScore(),
                            BigDecimal.valueOf(Math.round(avg * 10.0) / 10.0), count);
                });
    }

    // engagement é dono da tabela ratings: calcula avg/count aqui e empurra para work
    private RecalcResult recalcAndPush(UUID workId) {
        double avg = ratingRepository.avgScoreByWorkId(workId);
        int count = ratingRepository.countByWorkId(workId);
        BigDecimal avgRounded = BigDecimal.valueOf(Math.round(avg * 10.0) / 10.0);
        updateWorkRatingStatsUseCase.handle(workId, avgRounded, count);
        return new RecalcResult(avgRounded, count);
    }

    private record RecalcResult(BigDecimal avg, int count) {}
}
