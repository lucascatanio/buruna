package com.buruna.manga.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// usado no PUT /my/mangas/{id}
public record PrivateMangaRequest(
        @NotBlank @Size(max = 255) String title,
        String synopsis
) {
}