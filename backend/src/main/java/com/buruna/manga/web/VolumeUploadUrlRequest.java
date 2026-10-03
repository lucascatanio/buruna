package com.buruna.manga.web;

import jakarta.validation.constraints.NotNull;

public record VolumeUploadUrlRequest(
        @NotNull Integer volumeNumber
) {}
