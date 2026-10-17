package com.buruna.sourcing.application;

import com.buruna.shared.media.ImageInspector;
import com.buruna.shared.source.ExternalSource;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.SourceChapterInfo;
import com.buruna.shared.source.SourceGroup;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.SourceUnavailableException;
import com.buruna.sourcing.domain.TrackedLanguage;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Consultas à fonte que a tela de importação faz antes de importar. */
@Service
public class SearchSourceUseCase {

    private static final Logger log = LoggerFactory.getLogger(SearchSourceUseCase.class);
    private static final int SEARCH_LIMIT = 20;
    private static final String COVER_PREFIX = "https://uploads.mangadex.org/covers/";
    private static final long MAX_THUMBNAIL_BYTES = 2L * 1024 * 1024;

    private final ExternalSource source;
    private final TrackedWorkRepository trackedWorkRepository;

    public SearchSourceUseCase(ExternalSource source, TrackedWorkRepository trackedWorkRepository) {
        this.source = source;
        this.trackedWorkRepository = trackedWorkRepository;
    }

    public List<SourceSearchResult> search(String title) {
        try {
            return source.search(title, SEARCH_LIMIT).stream()
                    .map(summary -> SourceSearchResult.from(summary,
                            trackedWorkRepository.existsBySourceAndExternalId(SourceName.MANGADEX, summary.externalId())))
                    .toList();
        } catch (ExternalSourceException e) {
            log.warn("Busca na fonte falhou: {}", title, e);
            throw new SourceUnavailableException();
        }
    }

    /**
     * Miniatura da capa para a busca: a fonte recusa hotlink, então a imagem passa pelo backend.
     * Só aceita endereço de capa da própria fonte, para o endpoint não virar proxy aberto.
     */
    public Optional<CoverThumbnail> coverThumbnail(String url) {
        if (url == null || !url.startsWith(COVER_PREFIX) || url.contains("..") || url.contains("?")) {
            return Optional.empty();
        }
        try {
            byte[] content = source.download(url, MAX_THUMBNAIL_BYTES);
            return ImageInspector.detectType(content).map(type -> new CoverThumbnail(content, type.contentType()));
        } catch (ExternalSourceException e) {
            log.info("Miniatura não baixou: {}", url);
            return Optional.empty();
        }
    }

    public record CoverThumbnail(byte[] content, String contentType) {
    }

    /** Grupos que traduziram a obra no idioma, do que tem mais capítulos para o que tem menos. */
    public List<SourceGroupSummary> groups(String externalId, String language) {
        String tag = TrackedLanguage.of(language, null, null).getLanguage();
        List<SourceChapterInfo> versions;
        try {
            versions = source.listChapters(externalId, Set.of(tag));
        } catch (ExternalSourceException e) {
            log.warn("Listagem de grupos na fonte falhou: {} {}", externalId, tag, e);
            throw new SourceUnavailableException();
        }
        Map<String, SourceGroupSummary> groups = new LinkedHashMap<>();
        for (SourceChapterInfo version : versions) {
            for (SourceGroup group : version.groups()) {
                groups.merge(group.id(), new SourceGroupSummary(group.id(), group.name(), 1),
                        (a, b) -> new SourceGroupSummary(a.id(), a.name(), a.chapterCount() + 1));
            }
        }
        return groups.values().stream()
                .sorted(Comparator.comparingInt(SourceGroupSummary::chapterCount).reversed())
                .toList();
    }
}
