package com.buruna.sourcing.application;

import com.buruna.shared.jobs.JobLaunchException;
import com.buruna.shared.jobs.JobLauncher;
import com.buruna.shared.transaction.AfterCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispara o Cloud Run Job no modo fonte. Se o disparo falhar, nada se perde: as versões ficam
 * PENDING e a próxima rodada (outro import ou o sync) as baixa.
 */
@Component
@Profile("!local")
public class CloudRunSourcingTrigger implements SourcingTrigger {

    private static final Logger log = LoggerFactory.getLogger(CloudRunSourcingTrigger.class);

    private final JobLauncher jobLauncher;

    public CloudRunSourcingTrigger(JobLauncher jobLauncher) {
        this.jobLauncher = jobLauncher;
    }

    @Override
    public void requestRun() {
        AfterCommit.run(() -> {
            try {
                jobLauncher.run(List.of(SourcingJobArgs.run()));
            } catch (JobLaunchException e) {
                log.error("Não foi possível disparar o executor de fonte", e);
            }
        });
    }
}
