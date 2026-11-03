package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class FindPublicWorkUseCase {

    private final WorkRepository workRepository;

    public FindPublicWorkUseCase(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    @Transactional(readOnly = true)
    public WorkInfo getPublicWorkInfo(UUID workId) {
        return workRepository.findById(workId)
                .filter(m -> m.isPublic())
                .map(m -> new WorkInfo(m.getId(), m.getSlug(), m.getTitle(), m.getCoverUrl()))
                .orElseThrow(() -> new WorkNotFoundException(workId));
    }

    /** Obra pública ligada a esta obra do AniList (ADR-54), se houver. */
    @Transactional(readOnly = true)
    public Optional<WorkInfo> findByAniListId(int aniListId) {
        return workRepository.findFirstByAniListId(aniListId)
                .filter(Work::isPublic)
                .map(m -> new WorkInfo(m.getId(), m.getSlug(), m.getTitle(), m.getCoverUrl()));
    }

    @Transactional(readOnly = true)
    public void requirePublicWork(UUID workId) {
        getPublicWorkInfo(workId);
    }
}
