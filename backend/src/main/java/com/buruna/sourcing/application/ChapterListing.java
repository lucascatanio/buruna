package com.buruna.sourcing.application;

import com.buruna.shared.source.SourceChapterInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * O que uma fonte listou de uma obra acompanhada: as versões e os idiomas acompanhados que voltaram
 * vazios enquanto a fonte listou outros, o sinal de um código de idioma do site errado (#142).
 */
record ChapterListing(List<SourceChapterInfo> versions, List<EmptyLanguage> emptyLanguages) {

    ChapterListing {
        versions = List.copyOf(versions);
        emptyLanguages = emptyLanguages.stream().sorted(Comparator.comparing(EmptyLanguage::sourceCode)).toList();
    }

    static ChapterListing of(List<SourceChapterInfo> versions) {
        return new ChapterListing(versions, List.of());
    }

    /** Idioma pedido com o código do site ({@code ""}: o site não informa) e os que o site listou. */
    record EmptyLanguage(String sourceCode, List<String> listedCodes) {
    }

    /** "Nenhum capítulo em pt-br; o site listou en, es-la." Vazio quando todo idioma veio com capítulos. */
    Optional<String> notice() {
        if (emptyLanguages.isEmpty()) {
            return Optional.empty();
        }
        String reasons = emptyLanguages.stream()
                .map(empty -> "Nenhum capítulo " + (empty.sourceCode().isEmpty() ? "sem idioma informado" : "em " + empty.sourceCode())
                        + "; o site listou " + String.join(", ", empty.listedCodes()) + ".")
                .collect(Collectors.joining(" "));
        return Optional.of(reasons + " Confira o código no painel Idiomas.");
    }
}
