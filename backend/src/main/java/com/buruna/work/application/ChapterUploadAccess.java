package com.buruna.work.application;

import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterNotFoundException;
import com.buruna.work.domain.PublicChapterOnPrivateWorkException;
import com.buruna.work.domain.Work;
import com.buruna.work.persistence.ChapterRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Posse da obra para operar capítulos, nos dois escopos (ADR-35: posse por actorId). */
@Component
public class ChapterUploadAccess {

    private final PrivateWorkAccess privateAccess;
    private final PublicWorkAccess publicAccess;
    private final ChapterRepository chapterRepository;

    public ChapterUploadAccess(PrivateWorkAccess privateAccess, PublicWorkAccess publicAccess,
                               ChapterRepository chapterRepository) {
        this.privateAccess = privateAccess;
        this.publicAccess = publicAccess;
        this.chapterRepository = chapterRepository;
    }

    public Work findWork(UUID workId, ChapterScope scope, ChapterActor actor) {
        if (scope == ChapterScope.PRIVATE) {
            return privateAccess.findOwned(workId, actor.actorId());
        }
        Work work = publicAccess.findModifiable(workId, actor.actorId(), actor.isAdmin());
        if (!work.isPublic()) {
            throw new PublicChapterOnPrivateWorkException();
        }
        return work;
    }

    /** Capítulo de outra obra responde como inexistente, sem revelar que existe. */
    public Chapter findChapter(UUID workId, UUID chapterId, ChapterScope scope, ChapterActor actor) {
        findWork(workId, scope, actor);
        return chapterRepository.findWithPagesById(chapterId)
                .filter(chapter -> chapter.getWorkId().equals(workId))
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));
    }
}
