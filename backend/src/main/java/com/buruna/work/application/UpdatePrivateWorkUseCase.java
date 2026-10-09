package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Atualiza título/sinopse de um mangá da coleção privada do ator. */
@Service
public class UpdatePrivateWorkUseCase {

    private final WorkRepository workRepository;
    private final PrivateWorkAccess access;
    private final PrivateWorkMapper mapper;

    public UpdatePrivateWorkUseCase(WorkRepository workRepository,
                                     PrivateWorkAccess access,
                                     PrivateWorkMapper mapper) {
        this.workRepository = workRepository;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateWorkResponse handle(UUID id, PrivateWorkRequest request, UUID actorId) {
        Work work = access.findOwned(id, actorId);
        work.updatePrivateDetails(request.title(), request.synopsis());
        return mapper.toResponse(workRepository.save(work));
    }
}
