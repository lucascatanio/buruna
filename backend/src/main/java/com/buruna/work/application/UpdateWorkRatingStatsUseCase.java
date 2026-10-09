package com.buruna.work.application;

import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class UpdateWorkRatingStatsUseCase {

    private final WorkRepository workRepository;

    public UpdateWorkRatingStatsUseCase(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    @Transactional
    public void handle(UUID workId, BigDecimal avgRating, int ratingCount) {
        var work = workRepository.findById(workId)
                .orElseThrow(() -> new WorkNotFoundException(workId));
        work.applyRatingStats(avgRating, ratingCount);
        workRepository.save(work);
    }
}
