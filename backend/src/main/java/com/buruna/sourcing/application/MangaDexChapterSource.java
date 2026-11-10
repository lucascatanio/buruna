package com.buruna.sourcing.application;

import com.buruna.shared.media.ImageInspector;
import com.buruna.shared.media.ImageInspector.InspectedImage;
import com.buruna.shared.media.InvalidArchiveException;
import com.buruna.shared.source.ExternalSource;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.SourcePage;
import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.TrackedWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * O MangaDex pela API (ADR-47): cada página é baixada nas duas qualidades que ele oferece, com os
 * bytes originais (ADR-48).
 */
@Component
class MangaDexChapterSource implements ChapterSource {

    private static final Logger log = LoggerFactory.getLogger(MangaDexChapterSource.class);
    private static final long MB = 1024L * 1024;

    private final ExternalSource source;
    private final long maxPageBytes;

    MangaDexChapterSource(ExternalSource source, @Value("${app.ingest.max-page-mb}") long maxPageMb) {
        this.source = source;
        this.maxPageBytes = maxPageMb * MB;
    }

    @Override
    public SourceName source() {
        return SourceName.MANGADEX;
    }

    @Override
    public ChapterListing listChapters(TrackedWork tracked) {
        return ChapterListing.of(source.listChapters(tracked.getExternalId(), tracked.languageTags()));
    }

    @Override
    public Set<String> availableLanguages(TrackedWork tracked) {
        return new HashSet<>(source.fetchWork(tracked.getExternalId()).availableLanguages());
    }

    @Override
    public void fetchPages(TrackedWork tracked, SourceChapter version, Consumer<FetchedPage> sink) {
        List<SourcePage> pages = source.fetchPages(version.getExternalId());
        if (pages.isEmpty()) {
            throw new ExternalSourceException("A fonte não devolveu nenhuma página");
        }
        for (int i = 0; i < pages.size(); i++) {
            SourcePage page = pages.get(i);
            InspectedImage original = ImageInspector.inspect(source.download(page.url(), maxPageBytes), page.url());
            Optional<InspectedImage> dataSaver = page.dataSaverUrl() == null
                    ? Optional.empty() : dataSaver(i + 1, page.dataSaverUrl());
            sink.accept(new FetchedPage(original, dataSaver.orElse(null)));
        }
    }

    // a versão leve é um extra: se falhar, a página fica só com a original
    private Optional<InspectedImage> dataSaver(int position, String url) {
        try {
            return Optional.of(ImageInspector.inspect(source.download(url, maxPageBytes), url));
        } catch (ExternalSourceException | InvalidArchiveException e) {
            log.info("Página {} sem versão leve: {}", position, e.getMessage());
            return Optional.empty();
        }
    }
}
