package com.buruna.shared.jobs;

import java.util.List;

/** Dispara uma execução do Cloud Run Job de ingest com os argumentos dados. */
public interface JobLauncher {

    void run(List<String> args);
}
