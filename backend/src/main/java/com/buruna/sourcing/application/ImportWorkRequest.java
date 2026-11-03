package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.SourceName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Pedido de importação. {@code source} nulo é o MangaDex; no manga-downloader (ADR-54),
 * {@code externalId} é a URL da série e {@code linkWorkId} é obrigatório. {@code linkWorkId}: obra
 * do Burūna que já existe e passa a ser acompanhada, em vez de criar outra.
 */
public record ImportWorkRequest(
        @NotBlank @Size(max = 500) String externalId,
        @NotEmpty @Valid List<LanguageChoice> languages,
        UUID linkWorkId,
        SourceName source
) {

    /**
     * Idioma acompanhado e, opcionalmente, o grupo de tradução preferido nele. Num site,
     * {@code sourceLanguage} é o código do idioma como o site o escreve (vazio quando o site não
     * informa) e {@code language} é a tag do Burūna que ele vira.
     */
    public record LanguageChoice(
            @NotBlank @Size(max = 35) String language,
            @Size(max = 100) String preferredGroupId,
            @Size(max = 255) String preferredGroupName,
            @Size(max = 35) String sourceLanguage
    ) {
    }
}
