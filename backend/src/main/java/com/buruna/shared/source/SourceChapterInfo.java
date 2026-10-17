package com.buruna.shared.source;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Uma versão de capítulo na fonte: o mesmo número pode ter várias, uma por grupo.
 * {@code number} vem como texto ("10", "10.5") e é nulo em oneshot e extras.
 */
public record SourceChapterInfo(
        String externalId,
        String language,
        String number,
        String volume,
        String title,
        List<SourceGroup> groups,
        int pages,
        OffsetDateTime publishedAt,
        OffsetDateTime updatedAt
) {
}
