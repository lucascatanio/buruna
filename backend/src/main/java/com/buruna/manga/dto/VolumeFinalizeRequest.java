package com.buruna.manga.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VolumeFinalizeRequest(
        @NotBlank String objectName,
        @NotNull Integer volumeNumber
) {}
