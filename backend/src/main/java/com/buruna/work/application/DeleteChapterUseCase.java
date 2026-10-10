package com.buruna.work.application;

import com.buruna.work.domain.Chapter;
import com.buruna.work.persistence.ChapterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Apaga um capítulo e, depois do commit, os objetos dele no storage. Um capítulo apagado no
 * meio da extração não volta: o Job encontra o capítulo ausente e descarta as páginas.
 */
@Service
public class DeleteChapterUseCase {

    private final ChapterUploadAccess access;
    private final ChapterRepository chapterRepository;
    private final ChapterStorageCleaner storageCleaner;

    public DeleteChapterUseCase(ChapterUploadAccess access, ChapterRepository chapterRepository,
                                ChapterStorageCleaner storageCleaner) {
        this.access = access;
        this.chapterRepository = chapterRepository;
        this.storageCleaner = storageCleaner;
    }

    @Transactional
    public void handle(UUID workId, UUID chapterId, ChapterScope scope, ChapterActor actor) {
        Chapter chapter = access.findChapter(workId, chapterId, scope, actor);
        List<String> objectNames = ChapterStorageCleaner.objectNamesOf(chapter);
        chapterRepository.delete(chapter);
        AfterCommit.run(() -> storageCleaner.deleteQuietly(objectNames));
    }
}
