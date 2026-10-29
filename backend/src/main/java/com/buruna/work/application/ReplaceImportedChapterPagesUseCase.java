package com.buruna.work.application;

import com.buruna.work.domain.ChapterObjectName;
import com.buruna.work.domain.ChapterPage;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.persistence.ChapterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Troca as páginas de um capítulo importado de fonte externa, mantendo o id do capítulo e o
 * progresso de quem lê (trocar de versão, baixar de novo). As páginas novas já estão no storage,
 * num subprefixo da revisão ({@link #pageObjectName}); as antigas são devolvidas para quem chama
 * apagar depois do commit.
 */
@Service
public class ReplaceImportedChapterPagesUseCase {

    private final ChapterRepository chapterRepository;

    public ReplaceImportedChapterPagesUseCase(ChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    public static String pageObjectName(UUID chapterId, String revision, int position, String extension) {
        return ChapterObjectName.revisionPage(chapterId, revision, position, extension);
    }

    public static String dataSaverObjectName(UUID chapterId, String revision, int position, String extension) {
        return ChapterObjectName.revisionDataSaverPage(chapterId, revision, position, extension);
    }

    /** Vazio quando o capítulo não existe mais ou não está publicado: quem chama apaga as páginas novas. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<List<String>> handle(UUID chapterId, List<ImportedPage> pages, String scanlationGroup, String title) {
        return chapterRepository.findWithPagesById(chapterId)
                .filter(chapter -> chapter.getStatus() == ChapterStatus.PUBLISHED)
                .map(chapter -> chapter.replacePages(pages.stream()
                        .map(page -> ChapterPage.of(page.position(), page.objectName(), page.dataSaverObjectName(),
                                page.width(), page.height(), page.sizeBytes()))
                        .toList(), scanlationGroup, title));
    }
}
