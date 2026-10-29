package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.domain.PrivateWorkAccessDeniedException;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Carrega um mangá da coleção privada garantindo posse (ownership) por {@code actorId},
 * concentrando a regra antes espalhada em PrivateWorkService (ADR-35). RBAC fica na
 * borda (@PreAuthorize); aqui só posse. Um mangá público é tratado como inexistente na
 * coleção privada (404), preservando o comportamento atual.
 */
@Component
public class PrivateWorkAccess {

    private final WorkRepository workRepository;

    public PrivateWorkAccess(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    public Work findOwned(UUID workId, UUID actorId) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new WorkNotFoundException(workId));
        if (work.isPublic()) {
            throw new WorkNotFoundException(workId);
        }
        if (!work.getOwnerId().equals(actorId)) {
            throw new PrivateWorkAccessDeniedException();
        }
        return work;
    }
}
