package com.buruna.manga.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrivateMangaCreateRequest(
        @NotBlank @Size(max = 255) String title,
        String synopsis,
        String coverBase64
) {}
