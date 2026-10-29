package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkFormat;
import com.buruna.work.domain.WorkStatusOrigin;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.domain.Language;
import com.buruna.work.persistence.ChapterRepository;
import com.buruna.work.persistence.VolumeRepository;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.work.persistence.WorkSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Busca paginada do catálogo público com filtros (título/formato/status/tags AND/idioma), com
 * os idiomas de cada obra para o selo do card.
 *
 * <p>Preserva o two-step fetch (ADR-16): pagina os mangás por Specification e depois faz
 * batch-load das tags por id, em vez de um {@code @EntityGraph} numa query só (que
 * inflaria a paginação no JOIN das tags). A lista vem sem volumes (catálogo).
 */
@Service
public class CatalogQueryUseCase {

    private final WorkRepository workRepository;
    private final ChapterRepository chapterRepository;
    private final VolumeRepository volumeRepository;
    private final WorkResponseMapper mapper;

    public CatalogQueryUseCase(WorkRepository workRepository, ChapterRepository chapterRepository,
                               VolumeRepository volumeRepository, WorkResponseMapper mapper) {
        this.workRepository = workRepository;
        this.chapterRepository = chapterRepository;
        this.volumeRepository = volumeRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<WorkResponse> handle(String title, WorkFormat format,
                                      WorkStatusOrigin statusOrigin, Set<UUID> tagIds, String language,
                                      Pageable pageable) {
        String languageTag = language == null || language.isBlank() ? null : Language.of(language).value();
        Specification<Work> spec = Specification
                .where(WorkSpecification.isPublic())
                .and(WorkSpecification.hasLanguage(languageTag))
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

        Map<UUID, List<String>> languages = languagesOf(ids);
        return page.map(m -> mapper.toResponse(withTags.getOrDefault(m.getId(), m), false)
                .withLanguages(languages.getOrDefault(m.getId(), List.of())));
    }

    /** Idiomas de todo o catálogo público, para o filtro. */
    @Transactional(readOnly = true)
    public List<String> availableLanguages() {
        Set<String> languages = new TreeSet<>(chapterRepository.findPublicLanguages(ChapterStatus.PUBLISHED));
        if (volumeRepository.existsInPublicWorks()) {
            languages.add(WorkSpecification.LEGACY_VOLUME_LANGUAGE);
        }
        return List.copyOf(languages);
    }

    private Map<UUID, List<String>> languagesOf(List<UUID> workIds) {
        if (workIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<String>> byWork = new HashMap<>();
        chapterRepository.findLanguagesByWorkIds(workIds, ChapterStatus.PUBLISHED).forEach(row ->
                byWork.computeIfAbsent(row.getWorkId(), id -> new TreeSet<>()).add(row.getLanguage()));
        volumeRepository.findWorkIdsWithVolumes(workIds).forEach(id ->
                byWork.computeIfAbsent(id, key -> new TreeSet<>()).add(WorkSpecification.LEGACY_VOLUME_LANGUAGE));
        Map<UUID, List<String>> result = new HashMap<>();
        byWork.forEach((id, set) -> result.put(id, List.copyOf(set)));
        return result;
    }
}
