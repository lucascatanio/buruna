package com.buruna.sourcing.application;

import java.util.List;

/**
 * O que a fonte tem de uma obra num idioma, antes de importar: versões de capítulo que dá para
 * baixar, quantas já foram publicadas (incluindo as retiradas e as que só apontam para fora) e
 * os grupos, do que tem mais versões para o que tem menos.
 */
public record LanguageAvailability(String language, int availableChapters, int publishedChapters,
                                   List<SourceGroupSummary> groups) {
}
