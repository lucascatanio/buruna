package com.buruna.work.application;

import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetWorkInfoUseCase {

    private final WorkRepository workRepository;

    public GetWorkInfoUseCase(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    @Transactional(readOnly = true)
    public Map<UUID, WorkInfo> getInfoByIds(Collection<UUID> workIds) {
        if (workIds.isEmpty()) return Map.of();
        return workRepository.findAllById(workIds).stream()
                .collect(Collectors.toMap(
                        m -> m.getId(),
                        m -> new WorkInfo(m.getId(), m.getSlug(), m.getTitle(), m.getCoverUrl())
                ));
    }

    /** Lança WorkNotFoundException se o mangá não existir — usado pelo contexto reading. */
    @Transactional(readOnly = true)
    public void requireExists(UUID workId) {
        if (!workRepository.existsById(workId)) {
            throw new WorkNotFoundException(workId);
        }
    }
}
