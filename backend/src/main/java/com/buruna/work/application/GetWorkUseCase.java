package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Detalhe de um mangá público resolvendo slug OU UUID no mesmo endpoint (ADR-13): tenta
 * por UUID, cai para slug, e exige que seja público. Inclui os volumes.
 */
@Service
public class GetWorkUseCase {

    private final WorkRepository workRepository;
    private final WorkResponseMapper mapper;

    public GetWorkUseCase(WorkRepository workRepository, WorkResponseMapper mapper) {
        this.workRepository = workRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public WorkResponse handle(String slugOrId) {
        Work work = parseUuid(slugOrId)
                .flatMap(workRepository::findById)
                .or(() -> workRepository.findBySlug(slugOrId))
                .filter(Work::isPublic)
                .orElseThrow(() -> new WorkNotFoundException(slugOrId));
        return mapper.toResponse(work, true);
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
