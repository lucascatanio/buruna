package com.buruna.sourcing.web;

import com.buruna.sourcing.application.SourcingJobArgs;
import com.buruna.sourcing.application.SourcingRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Entrada do Cloud Run Job no modo fonte: o mesmo Job de ingest (ADR-48) recebe
 * {@code --sourcing.run=true}, faz uma rodada do executor e encerra. Sem esse argumento, quem
 * trata a execução é o {@code IngestJobRunner} (capítulo enviado).
 */
@Component
@Profile("ingest")
public class SourcingJobRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SourcingJobRunner.class);

    private final SourcingRunner runner;
    private final ConfigurableApplicationContext context;
    // id da execução que o Cloud Run injeta; identifica quem segura o lease nos logs
    private final String executionId;

    public SourcingJobRunner(SourcingRunner runner, ConfigurableApplicationContext context,
                             @Value("${CLOUD_RUN_EXECUTION:}") String executionId) {
        this.runner = runner;
        this.context = context;
        this.executionId = executionId;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(SourcingJobArgs.RUN_OPTION)) {
            return;
        }
        String holder = executionId.isBlank() ? "job-" + UUID.randomUUID() : executionId;
        log.info("Rodada do executor de fonte {}", holder);
        runner.run(holder);
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
