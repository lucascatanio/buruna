package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.SourceName;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** A {@link ChapterSource} de cada fonte. */
@Component
class ChapterSources {

    private final Map<SourceName, ChapterSource> bySource = new EnumMap<>(SourceName.class);

    ChapterSources(List<ChapterSource> sources) {
        sources.forEach(source -> bySource.put(source.source(), source));
    }

    ChapterSource of(SourceName source) {
        ChapterSource chapterSource = bySource.get(source);
        if (chapterSource == null) {
            throw new IllegalStateException("Fonte sem implementação: " + source);
        }
        return chapterSource;
    }
}
