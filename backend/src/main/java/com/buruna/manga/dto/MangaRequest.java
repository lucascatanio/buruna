package com.buruna.manga.dto;

import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.domain.MangaStatusSite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MangaRequest(

        @NotBlank(message = "Título é obrigatório")
        String title,

        List<String> alternativeTitles,

        String synopsis,

        // Data URI ou base64 puro opcional.
        String coverBase64,

        @NotNull(message = "Formato é obrigatório")
        MangaFormat format,

        String originCountry,

        @NotNull(message = "Status de origem é obrigatório")
        MangaStatusOrigin statusOrigin,

        @NotNull(message = "Status no site é obrigatório")
        MangaStatusSite statusSite,

        @Min(value = 1800, message = "Ano inválido")
        @Max(value = 2100, message = "Ano inválido")
        Integer year,

        List<String> contentWarnings,

        Set<UUID> tagIds

) {
}