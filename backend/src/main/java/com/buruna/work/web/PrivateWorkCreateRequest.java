package com.buruna.work.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrivateWorkCreateRequest(
        @NotBlank @Size(max = 255) String title,
        String synopsis,
        String coverBase64
) {}
