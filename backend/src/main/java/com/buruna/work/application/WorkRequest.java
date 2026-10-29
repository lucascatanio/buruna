package com.buruna.work.application;

import com.buruna.work.domain.WorkFormat;
import com.buruna.work.domain.WorkStatusOrigin;
import com.buruna.work.domain.WorkStatusSite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record WorkRequest(

        @NotBlank(message = "Título é obrigatório")
        @Size(max = 255, message = "Título deve ter no máximo 255 caracteres")
        String title,

        List<String> alternativeTitles,

        String synopsis,

        // Data URI ou base64 puro opcional.
        String coverBase64,

        @NotNull(message = "Formato é obrigatório")
        WorkFormat format,

        @Size(max = 100, message = "País de origem deve ter no máximo 100 caracteres")
        String originCountry,

        @NotNull(message = "Status de origem é obrigatório")
        WorkStatusOrigin statusOrigin,

        @NotNull(message = "Status no site é obrigatório")
        WorkStatusSite statusSite,

        @Min(value = 1800, message = "Ano inválido")
        @Max(value = 2100, message = "Ano inválido")
        Integer year,

        List<String> contentWarnings,

        Set<UUID> tagIds

) {
}