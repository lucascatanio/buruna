package com.buruna.work.application;

import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterNumber;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Processa de novo um capítulo que falhou, com o arquivo já enviado, sem o usuário precisar
 * subir tudo outra vez. O número pode ter sido ocupado nesse meio-tempo, então é conferido sob
 * o mesmo lock do registro (ADR-53).
 */
@Service
public class RetryChapterIngestUseCase {

    private final ChapterUploadAccess access;
    private final RegisterChapterUseCase registerChapter;
    private final ChapterIngestTrigger ingestTrigger;

    public RetryChapterIngestUseCase(ChapterUploadAccess access, RegisterChapterUseCase registerChapter,
                                     ChapterIngestTrigger ingestTrigger) {
        this.access = access;
        this.registerChapter = registerChapter;
        this.ingestTrigger = ingestTrigger;
    }

    @Transactional
    public ChapterResponse handle(UUID workId, UUID chapterId, ChapterScope scope, ChapterActor actor) {
        access.findWork(workId, scope, actor);
        registerChapter.lockWork(workId);
        Chapter chapter = access.findChapter(workId, chapterId, scope, actor);
        // o estado vem antes do número: retry de capítulo publicado é pedido inválido (400),
        // não conflito de número com ele mesmo
        chapter.retry();
        registerChapter.assertNumberFree(workId, chapter.getLanguage().value(),
                chapter.getNumber().map(ChapterNumber::value).orElse(null), chapter.getId());

        ingestTrigger.requestIngest(chapter.getId());
        return ChapterResponse.from(chapter);
    }
}
