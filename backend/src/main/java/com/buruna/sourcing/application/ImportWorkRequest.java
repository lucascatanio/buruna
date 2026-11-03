package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.SourceName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Pedido de importação. {@code source} nulo é o MangaDex. {@code linkWorkId}: obra do Burūna que
 * já existe e passa a ser acompanhada, em vez de criar outra.
 *
 * <p>No manga-downloader (ADR-54), {@code externalId} é a URL da série, e a obra vem de um dos
 * dois: {@code linkWorkId} ou {@code aniListId}, a obra do AniList de onde saem os metadados
 * (anexa à obra que já tem esse id, ou cria). {@code synopsis} é a sinopse que o ADM revisou;
 * vazia, fica a do AniList, em inglês.
 */
public record ImportWorkRequest(
        @NotBlank @Size(max = 500) String externalId,
        @NotEmpty @Valid List<LanguageChoice> languages,
        UUID linkWorkId,
        SourceName source,
        @Positive Integer aniListId,
        @Size(max = 10000) String synopsis
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
