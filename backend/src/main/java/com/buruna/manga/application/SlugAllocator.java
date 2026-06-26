package com.buruna.manga.application;

import com.buruna.manga.domain.Slug;
import com.buruna.manga.repository.MangaRepository;
import org.springframework.stereotype.Component;

/**
 * Resolve um slug único a partir do título: a normalização é pura (VO {@link Slug}); a
 * resolução de unicidade (sufixo numérico) depende do repositório e por isso mora na
 * application (ADR-34 §4.3). Substitui o {@code uniqueSlug} duplicado em PrivateMangaService.
 */
@Component
public class SlugAllocator {

    private final MangaRepository mangaRepository;

    public SlugAllocator(MangaRepository mangaRepository) {
        this.mangaRepository = mangaRepository;
    }

    public Slug allocate(String title) {
        Slug base = Slug.fromTitle(title);
        if (!mangaRepository.existsBySlug(base.value())) {
            return base;
        }
        int suffix = 2;
        while (mangaRepository.existsBySlug(base.withSuffix(suffix).value())) {
            suffix++;
        }
        return base.withSuffix(suffix);
    }
}
