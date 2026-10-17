package com.buruna.sourcing.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Pedido de importação. {@code linkWorkId}: obra do Burūna que já existe e passa a ser
 * acompanhada, em vez de criar outra.
 */
public record ImportWorkRequest(
        @NotBlank @Size(max = 100) String externalId,
        @NotEmpty @Valid List<LanguageChoice> languages,
        UUID linkWorkId
) {

    /** Idioma acompanhado e, opcionalmente, o grupo de tradução preferido nele. */
    public record LanguageChoice(
            @NotBlank @Size(max = 35) String language,
            @Size(max = 100) String preferredGroupId,
            @Size(max = 255) String preferredGroupName
    ) {
    }
}
