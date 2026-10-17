package com.buruna.work.application;

/** Tag pedida por nome e categoria, criada se ainda não existir ({@link EnsureTagsUseCase}). */
public record TagSpec(String name, String categoryName) {
}
