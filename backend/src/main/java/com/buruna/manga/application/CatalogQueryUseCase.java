package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.dto.MangaResponse;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.MangaSpecification;
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

    private final MangaRepository mangaRepository;
    private final MangaResponseMapper mapper;

    public CatalogQueryUseCase(MangaRepository mangaRepository, MangaResponseMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<MangaResponse> handle(String title, MangaFormat format,
                                      MangaStatusOrigin statusOrigin, Set<UUID> tagIds,
                                      Pageable pageable) {
        Specification<Manga> spec = Specification
                .where(MangaSpecification.isPublic())
                .and(MangaSpecification.titleContains(title))
                .and(MangaSpecification.hasFormat(format))
                .and(MangaSpecification.hasStatusOrigin(statusOrigin))
                .and(MangaSpecification.hasTagIds(tagIds));
        Page<Manga> page = mangaRepository.findAll(spec, pageable);

        // step 2: batch-load das tags só dos ids da página (ADR-16)
        List<UUID> ids = page.map(Manga::getId).toList();
        Map<UUID, Manga> withTags = mangaRepository.findAllWithTagsByIdIn(ids)
                .stream()
                .collect(Collectors.toMap(Manga::getId, m -> m));

        return page.map(m -> mapper.toResponse(withTags.getOrDefault(m.getId(), m), false));
    }
}
