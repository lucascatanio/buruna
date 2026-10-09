package com.buruna.work.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// usado no PUT /my/works/{id}
public record PrivateWorkRequest(
        @NotBlank @Size(max = 255) String title,
        String synopsis
) {
}