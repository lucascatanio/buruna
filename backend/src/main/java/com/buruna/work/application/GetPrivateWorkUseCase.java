package com.buruna.work.application;

import com.buruna.work.domain.Work;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Lê um mangá da coleção privada do ator (com seus volumes). */
@Service
public class GetPrivateWorkUseCase {

    private final PrivateWorkAccess access;
    private final PrivateWorkMapper mapper;

    public GetPrivateWorkUseCase(PrivateWorkAccess access, PrivateWorkMapper mapper) {
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PrivateWorkResponse handle(UUID id, UUID actorId) {
        Work work = access.findOwned(id, actorId);
        return mapper.toResponse(work);
    }
}
