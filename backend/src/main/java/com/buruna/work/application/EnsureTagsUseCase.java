package com.buruna.work.application;

import com.buruna.work.domain.Slug;
import com.buruna.work.domain.Tag;
import com.buruna.work.domain.TagCategory;
import com.buruna.work.persistence.TagCategoryRepository;
import com.buruna.work.persistence.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Devolve os ids das tags pedidas, criando as que faltam (importação de uma fonte externa). A
 * tag é encontrada pelo slug ou pelo nome, então "Ação" pedida de novo cai na tag que já existe. Tag
 * apagada pelo ADM não volta: fica de fora. Categoria inexistente é criada.
 */
@Service
public class EnsureTagsUseCase {

    private final TagRepository tagRepository;
    private final TagCategoryRepository categoryRepository;

    public EnsureTagsUseCase(TagRepository tagRepository, TagCategoryRepository categoryRepository) {
        this.tagRepository = tagRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Set<UUID> handle(Collection<TagSpec> specs) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (TagSpec spec : specs) {
            if (spec.name() == null || spec.name().isBlank()) {
                continue;
            }
            String name = spec.name().trim();
            // pelo nome também: tag antiga pode ter slug diferente do que o nome geraria hoje
            Optional<Tag> existing = tagRepository.findBySlug(Slug.fromTitle(name).value())
                    .or(() -> tagRepository.findFirstByNameIgnoreCase(name));
            if (existing.isPresent()) {
                if (existing.get().isActive()) {
                    ids.add(existing.get().getId());
                }
                continue;
            }
            Tag created = tagRepository.save(new Tag(name, Slug.fromTitle(name).value(), category(spec.categoryName())));
            ids.add(created.getId());
        }
        return ids;
    }

    private TagCategory category(String name) {
        return categoryRepository.findByName(name)
                .orElseGet(() -> categoryRepository.save(new TagCategory(name)));
    }
}
