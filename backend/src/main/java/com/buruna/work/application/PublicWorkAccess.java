package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkModificationDeniedException;
import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Carrega um mangá para modificação aplicando a regra "dono OU ADMIN" (ADR-35). O papel
 * chega como primitivo {@code isAdmin} da borda; a posse é por {@code actorId}. RBAC de
 * papel (COLLABORATOR/ADMIN) fica no @PreAuthorize do controller. Concentra o antigo
 * {@code assertCanModify} de WorkService/VolumeService.
 */
@Component
public class PublicWorkAccess {

    private final WorkRepository workRepository;

    public PublicWorkAccess(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    public Work findModifiable(UUID workId, UUID actorId, boolean isAdmin) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new WorkNotFoundException(workId));
        if (!isAdmin && !work.getOwnerId().equals(actorId)) {
            throw new WorkModificationDeniedException();
        }
        return work;
    }
}
