package com.buruna.sourcing.application;

import com.buruna.shared.media.ImageInspector;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.ExternalWorkNotFoundException;
import com.buruna.shared.source.SourceWorkDetails;
import com.buruna.shared.source.WorkMetadataSource;
import com.buruna.sourcing.domain.SourceUnavailableException;
import com.buruna.sourcing.domain.SourceWorkNotFoundException;
import com.buruna.work.application.FindPublicWorkUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Consultas à base de metadados (AniList, ADR-54) que o import faz antes de criar a obra: busca,
 * prévia dos campos como a obra vai entrar e miniatura da capa.
 */
@Service
public class SearchWorkMetadataUseCase {

    private static final Logger log = LoggerFactory.getLogger(SearchWorkMetadataUseCase.class);
    private static final String SOURCE_NAME = "AniList";
    private static final int SEARCH_LIMIT = 20;
    private static final long MAX_THUMBNAIL_BYTES = 2L * 1024 * 1024;

    private final WorkMetadataSource source;
    private final FindPublicWorkUseCase findPublicWork;

    public SearchWorkMetadataUseCase(WorkMetadataSource source, FindPublicWorkUseCase findPublicWork) {
        this.source = source;
        this.findPublicWork = findPublicWork;
    }

    public List<WorkMetadataSearchResult> search(String title) {
        try {
            return source.search(title, SEARCH_LIMIT).stream().map(WorkMetadataSearchResult::from).toList();
        } catch (ExternalSourceException e) {
            log.warn("Busca no AniList falhou: {}", title, e);
            throw new SourceUnavailableException(SOURCE_NAME);
        }
    }

    /** Os campos da obra como o import vai gravá-los, para o ADM revisar (a sinopse vem em inglês). */
    public WorkMetadataPreview preview(String externalId) {
        try {
            SourceWorkDetails details = source.fetchWork(externalId);
            return WorkMetadataPreview.from(details, findPublicWork.findByAniListId(Integer.parseInt(details.externalId())));
        } catch (ExternalWorkNotFoundException e) {
            throw new SourceWorkNotFoundException(SOURCE_NAME, externalId);
        } catch (ExternalSourceException e) {
            log.warn("Obra {} do AniList não carregou", externalId, e);
            throw new SourceUnavailableException(SOURCE_NAME);
        }
    }

    /** Miniatura da capa pelo backend: o CSP do site não libera imagens do AniList. */
    public Optional<CoverThumbnail> coverThumbnail(String url) {
        try {
            byte[] content = source.download(url, MAX_THUMBNAIL_BYTES);
            return ImageInspector.detectType(content).map(type -> new CoverThumbnail(content, type.contentType()));
        } catch (ExternalSourceException e) {
            log.info("Miniatura do AniList não baixou: {}", url);
            return Optional.empty();
        }
    }
}
