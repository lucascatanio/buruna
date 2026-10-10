package com.buruna.work.web;

import com.buruna.work.application.IngestJobArgs;
import com.buruna.work.application.ProcessChapterSourceUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponto de entrada do Cloud Run Job de ingest (ADR-48): o mesmo jar do backend, no profile
 * {@code ingest}, recebe {@code --ingest.chapter-id=<id>}, processa e encerra. Tem o papel de
 * um controller: traduz a entrada (argumentos em vez de HTTP) e delega ao use case.
 *
 * <p>Falha de processamento já vira FAILED no capítulo e encerra com código 0: repetir a
 * tarefa não mudaria um arquivo inválido. Código 1 só para argumento ausente ou inválido.
 */
@Component
@Profile("ingest")
public class IngestJobRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestJobRunner.class);

    private final ProcessChapterSourceUseCase processChapterSource;
    private final ConfigurableApplicationContext context;

    public IngestJobRunner(ProcessChapterSourceUseCase processChapterSource, ConfigurableApplicationContext context) {
        this.processChapterSource = processChapterSource;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> values = args.getOptionValues(IngestJobArgs.CHAPTER_OPTION);
        Optional<UUID> chapterId = values == null || values.size() != 1
                ? Optional.empty()
                : IngestJobArgs.parseChapterId(values.get(0));
        if (chapterId.isEmpty()) {
            log.error("Argumento --{} ausente ou inválido: {}", IngestJobArgs.CHAPTER_OPTION, values);
            System.exit(SpringApplication.exit(context, () -> 1));
            return;
        }
        log.info("Processando capítulo {}", chapterId.get());
        processChapterSource.handle(chapterId.get());
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
