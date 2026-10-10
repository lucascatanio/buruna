package com.buruna.work.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Dev local e testes não têm Cloud Run Job: processa na própria requisição, após o commit. */
@Component
@Profile("local")
public class InlineChapterIngestTrigger implements ChapterIngestTrigger {

    private final ProcessChapterSourceUseCase processChapterSource;

    public InlineChapterIngestTrigger(ProcessChapterSourceUseCase processChapterSource) {
        this.processChapterSource = processChapterSource;
    }

    @Override
    public void requestIngest(UUID chapterId) {
        AfterCommit.run(() -> processChapterSource.handle(chapterId));
    }
}
