package com.buruna.sourcing.application;

import com.buruna.shared.source.SourceWorkDetails;
import com.buruna.work.application.ImportedWorkCommand;
import com.buruna.work.application.TagSpec;
import com.buruna.work.application.WorkInfo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A obra como o import vai criá-la, pelo mesmo mapeamento ({@link WorkMetadataMapper}).
 * {@code format} e {@code statusOrigin} são nomes dos enums da obra; {@code coverUrl} é da base e
 * só abre pelo backend; {@code catalogWork} é nulo quando o id ainda não tem obra no catálogo.
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
        String sourceUrl,
        CatalogWork catalogWork
) {
    /** Obra do catálogo já ligada a este id do AniList: o import anexa a ela em vez de criar. */
    public record CatalogWork(UUID id, String slug, String title) {
    }

    static WorkMetadataPreview from(SourceWorkDetails details, Optional<WorkInfo> existing) {
        ImportedWorkCommand work = WorkMetadataMapper.toCommand(details, null);
        return new WorkMetadataPreview(details.externalId(), work.title(), work.alternativeTitles(),
                work.synopsis(), work.format(), work.originCountry(), work.statusOrigin(), work.year(),
                work.contentWarnings(), work.tags(), details.coverUrl(), details.sourceUrl(),
                existing.map(info -> new CatalogWork(info.id(), info.slug(), info.title())).orElse(null));
    }
}
