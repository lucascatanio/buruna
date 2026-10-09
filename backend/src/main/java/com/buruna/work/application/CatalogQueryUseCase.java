package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkFormat;
import com.buruna.work.domain.WorkStatusOrigin;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.work.persistence.WorkSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Busca paginada do catálogo público com filtros (título/formato/status/tags AND).
 *
 * <p>Preserva o two-step fetch (ADR-16): pagina os mangás por Specification e depois faz
 * batch-load das tags por id, em vez de um {@code @EntityGraph} numa query só (que
 * inflaria a paginação no JOIN das tags). A lista vem sem volumes (catálogo).
 */
@Service
public class CatalogQueryUseCase {

    private final WorkRepository workRepository;
    private final WorkResponseMapper mapper;

    public CatalogQueryUseCase(WorkRepository workRepository, WorkResponseMapper mapper) {
        this.workRepository = workRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<WorkResponse> handle(String title, WorkFormat format,
                                      WorkStatusOrigin statusOrigin, Set<UUID> tagIds,
                                      Pageable pageable) {
        Specification<Work> spec = Specification
                .where(WorkSpecification.isPublic())
                .and(WorkSpecification.titleContains(title))
                .and(WorkSpecification.hasFormat(format))
                .and(WorkSpecification.hasStatusOrigin(statusOrigin))
                .and(WorkSpecification.hasTagIds(tagIds));
        Page<Work> page = workRepository.findAll(spec, pageable);

        // step 2: batch-load das tags só dos ids da página (ADR-16)
        List<UUID> ids = page.map(Work::getId).toList();
        Map<UUID, Work> withTags = workRepository.findAllWithTagsByIdIn(ids)
                .stream()
                .collect(Collectors.toMap(Work::getId, m -> m));

        return page.map(m -> mapper.toResponse(withTags.getOrDefault(m.getId(), m), false));
    }
}
