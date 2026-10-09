package com.buruna.work.application;

import com.buruna.work.domain.Slug;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Component;

/**
 * Resolve um slug único a partir do título: a normalização é pura (VO {@link Slug}); a
 * resolução de unicidade (sufixo numérico) depende do repositório e por isso mora na
 * application (ADR-34 §4.3). Substitui o {@code uniqueSlug} duplicado em PrivateWorkService.
 */
@Component
public class SlugAllocator {

    private final WorkRepository workRepository;

    public SlugAllocator(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    public Slug allocate(String title) {
        Slug base = Slug.fromTitle(title);
        if (!workRepository.existsBySlug(base.value())) {
            return base;
        }
        int suffix = 2;
        while (workRepository.existsBySlug(base.withSuffix(suffix).value())) {
            suffix++;
        }
        return base.withSuffix(suffix);
    }
}
