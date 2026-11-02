package com.buruna.sourcing.application;

import com.buruna.shared.source.SourceWorkDetails;
import com.buruna.work.application.ImportedWorkCommand;
import com.buruna.work.application.TagSpec;

import java.util.List;

/**
 * A obra como o import vai criá-la, pelo mesmo mapeamento ({@link WorkMetadataMapper}).
 * {@code format} e {@code statusOrigin} são nomes dos enums da obra; {@code coverUrl} é da base e
 * só abre pelo backend.
 */
public record WorkMetadataPreview(
        String externalId,
        String title,
        List<String> alternativeTitles,
        String synopsis,
        String format,
        String originCountry,
        String statusOrigin,
        Integer year,
        List<String> contentWarnings,
        List<TagSpec> tags,
        String coverUrl,
        String sourceUrl
) {
    static WorkMetadataPreview from(SourceWorkDetails details) {
        ImportedWorkCommand work = WorkMetadataMapper.toCommand(details, null);
        return new WorkMetadataPreview(details.externalId(), work.title(), work.alternativeTitles(),
                work.synopsis(), work.format(), work.originCountry(), work.statusOrigin(), work.year(),
                work.contentWarnings(), work.tags(), details.coverUrl(), details.sourceUrl());
    }
}
