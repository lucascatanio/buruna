package com.buruna.work.application;

import com.buruna.shared.jobs.JobLaunchException;
import com.buruna.shared.jobs.JobLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Dispara o Cloud Run Job de ingest (ADR-48). Se o disparo falhar, o capítulo vai para FAILED
 * com o arquivo mantido, para o usuário tentar de novo, em vez de ficar preso em PROCESSING.
 */
@Component
@Profile("!local")
public class CloudRunChapterIngestTrigger implements ChapterIngestTrigger {

    private static final Logger log = LoggerFactory.getLogger(CloudRunChapterIngestTrigger.class);

    private final JobLauncher jobLauncher;
    private final FailChapterIngestUseCase failChapterIngest;

    public CloudRunChapterIngestTrigger(JobLauncher jobLauncher, FailChapterIngestUseCase failChapterIngest) {
        this.jobLauncher = jobLauncher;
        this.failChapterIngest = failChapterIngest;
    }

    @Override
    public void requestIngest(UUID chapterId) {
        AfterCommit.run(() -> {
            try {
                jobLauncher.run(List.of(IngestJobArgs.chapter(chapterId)));
            } catch (JobLaunchException e) {
                log.error("Não foi possível disparar o ingest do capítulo {}", chapterId, e);
                failChapterIngest.keepingSource(chapterId,
                        "Não foi possível iniciar o processamento. Tente de novo.");
            }
        });
    }
}
