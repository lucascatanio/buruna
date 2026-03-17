package com.buruna.manga.dto;

import jakarta.validation.constraints.NotNull;

public record VolumeUploadUrlRequest(
        @NotNull Integer volumeNumber
) {}
