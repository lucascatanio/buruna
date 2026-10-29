package com.buruna.shared.source;

import java.util.List;
import java.util.Map;

/**
 * Metadados de uma obra na fonte. {@code descriptions} vai de idioma (BCP 47) para texto.
 * {@code longStrip}: a fonte marca a obra como rolagem vertical (webtoon).
 * {@code demographic}: público-alvo como a fonte informa, em minúsculas (shounen, shoujo, seinen, josei), ou nulo.
 */
public record SourceWorkDetails(
        String externalId,
        String title,
        List<String> alternativeTitles,
        Map<String, String> descriptions,
        String originalLanguage,
        SourceWorkStatus status,
        Integer year,
        SourceContentRating contentRating,
        List<SourceTag> tags,
        boolean longStrip,
        String coverUrl,
        String sourceUrl,
        String demographic,
        List<String> availableLanguages
) {
}
