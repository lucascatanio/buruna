package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkAlreadyExistsException;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Cria um mangá já público no catálogo. RBAC na borda; dono = ator (ADR-35). */
@Service
public class CreatePublicWorkUseCase {

    private final WorkRepository workRepository;
    private final SlugAllocator slugAllocator;
    private final WorkRequestApplier requestApplier;
    private final WorkResponseMapper mapper;

    public CreatePublicWorkUseCase(WorkRepository workRepository,
                                    SlugAllocator slugAllocator,
                                    WorkRequestApplier requestApplier,
                                    WorkResponseMapper mapper) {
        this.workRepository = workRepository;
        this.slugAllocator = slugAllocator;
        this.requestApplier = requestApplier;
        this.mapper = mapper;
    }

    @Transactional
    public WorkResponse handle(WorkRequest request, UUID actorId) {
        if (workRepository.existsByTitleIgnoreCaseAndIsPublicTrue(request.title())) {
            throw new WorkAlreadyExistsException(request.title());
        }

        Work work = Work.createPublic(slugAllocator.allocate(request.title()), actorId);
        requestApplier.apply(work, request);

        return mapper.toResponse(workRepository.save(work), true);
    }
}
