package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.SourceChapterState;
import com.buruna.sourcing.domain.TrackedWork;
import com.buruna.sourcing.persistence.SourceChapterRepository;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import com.buruna.work.application.FindPublicWorkUseCase;
import com.buruna.work.application.WorkInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lista das obras acompanhadas para a tela do ADM, com o andamento dos capítulos. */
@Service
public class ListTrackedWorksUseCase {

    private final TrackedWorkRepository trackedWorkRepository;
    private final SourceChapterRepository sourceChapterRepository;
    private final FindPublicWorkUseCase findPublicWork;

    public ListTrackedWorksUseCase(TrackedWorkRepository trackedWorkRepository,
                                   SourceChapterRepository sourceChapterRepository,
                                   FindPublicWorkUseCase findPublicWork) {
        this.trackedWorkRepository = trackedWorkRepository;
        this.sourceChapterRepository = sourceChapterRepository;
        this.findPublicWork = findPublicWork;
    }

    @Transactional(readOnly = true)
    public List<TrackedWorkResponse> handle() {
        return trackedWorkRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    /** Crédito na página da obra: de onde ela vem, se vier de uma fonte. */
    @Transactional(readOnly = true)
    public Optional<WorkSourceResponse> sourceOf(UUID workId) {
        return trackedWorkRepository.findByWorkId(workId)
                .map(tracked -> new WorkSourceResponse("MangaDex", SourceUrls.work(tracked)));
    }

    private TrackedWorkResponse toResponse(TrackedWork tracked) {
        List<SourceChapter> versions = sourceChapterRepository.findByTrackedWorkId(tracked.getId());
        Map<SourceChapterState, Long> counts = new EnumMap<>(SourceChapterState.class);
        for (SourceChapterState state : SourceChapterState.values()) {
            counts.put(state, 0L);
        }
        versions.forEach(version -> counts.merge(version.getState(), 1L, Long::sum));
        WorkInfo work = findPublicWork.getPublicWorkInfo(tracked.getWorkId());
        return TrackedWorkResponse.from(tracked, work.title(), work.slug(), counts,
                SourceAlerts.of(tracked, versions).size());
    }
}
