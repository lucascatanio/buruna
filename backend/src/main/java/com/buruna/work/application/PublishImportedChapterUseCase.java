package com.buruna.work.application;

import com.buruna.work.domain.ChapterObjectName;
import com.buruna.work.domain.ChapterPage;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.persistence.ChapterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Publica um capítulo importado de uma fonte externa, com as páginas que o importador já
 * gravou no storage (ADR-48). O capítulo foi registrado antes em processamento pelo
 * {@link RegisterChapterUseCase}. Os nomes dos objetos vêm de {@link #pageObjectName} e
 * {@link #dataSaverObjectName}, para ficarem no prefixo do capítulo e serem apagados com ele.
 */
@Service
public class PublishImportedChapterUseCase {

    private final ChapterRepository chapterRepository;
    private final Clock clock;

    public PublishImportedChapterUseCase(ChapterRepository chapterRepository, Clock clock) {
        this.chapterRepository = chapterRepository;
        this.clock = clock;
    }

    public static String pageObjectName(UUID chapterId, int position, String extension) {
        return ChapterObjectName.page(chapterId, position, extension);
    }

    public static String dataSaverObjectName(UUID chapterId, int position, String extension) {
        return ChapterObjectName.dataSaverPage(chapterId, position, extension);
    }

    /**
     * Falso quando o capítulo não está mais em processamento (apagado ou já resolvido):
     * quem chama apaga as páginas que gravou. Transação própria, porque quem chama baixou as
     * páginas fora de transação.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean handle(UUID chapterId, List<ImportedPage> pages) {
        return chapterRepository.findWithPagesById(chapterId)
                .filter(chapter -> chapter.getStatus() == ChapterStatus.PROCESSING)
                .map(chapter -> {
                    chapter.publishPages(pages.stream()
                            .map(page -> ChapterPage.of(page.position(), page.objectName(), page.dataSaverObjectName(),
                                    page.width(), page.height(), page.sizeBytes()))
                            .toList(), OffsetDateTime.now(clock));
                    return true;
                })
                .orElse(false);
    }

    /** Apaga o capítulo registrado que não chegou a ter páginas, sem passar pelo FAILED. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discard(UUID chapterId) {
        chapterRepository.findById(chapterId)
                .filter(chapter -> chapter.getStatus() == ChapterStatus.PROCESSING)
                .ifPresent(chapterRepository::delete);
    }
}
