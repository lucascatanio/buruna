package com.buruna.work.web;

import jakarta.validation.constraints.NotNull;

public record VolumeUploadUrlRequest(
        @NotNull Integer volumeNumber
) {}
