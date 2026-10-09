package com.buruna.work.application;

import com.buruna.work.persistence.WorkRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Lista, paginada, a coleção privada do ator. */
@Service
public class ListPrivateWorksUseCase {

    private final WorkRepository workRepository;
    private final PrivateWorkMapper mapper;

    public ListPrivateWorksUseCase(WorkRepository workRepository, PrivateWorkMapper mapper) {
        this.workRepository = workRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<PrivateWorkResponse> handle(UUID actorId, Pageable pageable) {
        return workRepository.findAllByOwnerIdAndIsPublicFalse(actorId, pageable)
                .map(mapper::toResponse);
    }
}
