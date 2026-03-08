package com.buruna.manga.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagCategoryRequest(
        @NotBlank @Size(max = 100) String name
) {
}
