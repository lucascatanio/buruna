package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Atualiza um mangá do catálogo. Posse "dono OU ADMIN" (ADR-35) via PublicWorkAccess. */
@Service
public class UpdateWorkUseCase {

    private final WorkRepository workRepository;
    private final PublicWorkAccess access;
    private final WorkRequestApplier requestApplier;
    private final WorkResponseMapper mapper;

    public UpdateWorkUseCase(WorkRepository workRepository,
                              PublicWorkAccess access,
                              WorkRequestApplier requestApplier,
                              WorkResponseMapper mapper) {
        this.workRepository = workRepository;
        this.access = access;
        this.requestApplier = requestApplier;
        this.mapper = mapper;
    }

    @Transactional
    public WorkResponse handle(UUID id, WorkRequest request, UUID actorId, boolean isAdmin) {
        Work work = access.findModifiable(id, actorId, isAdmin);
        requestApplier.apply(work, request);
        return mapper.toResponse(workRepository.save(work), true);
    }
}
