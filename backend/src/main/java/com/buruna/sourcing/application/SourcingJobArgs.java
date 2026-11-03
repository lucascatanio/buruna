package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.SourceName;

import java.util.List;

/** Argumentos que pedem ao Job de ingest uma rodada do executor de fonte. */
public final class SourcingJobArgs {

    public static final String RUN_OPTION = "sourcing.run";
    /** Passado pelo Cloud Scheduler a cada 3 h (ADR-49). */
    public static final String SYNC_OPTION = "sourcing.sync";
    /** Fonte da rodada (ADR-54); sem ele, o MangaDex, como antes de haver outra fonte. */
    public static final String SOURCE_OPTION = "sourcing.source";

    private SourcingJobArgs() {
    }

    static List<String> run(SourceName source) {
        return List.of("--" + RUN_OPTION + "=true", "--" + SOURCE_OPTION + "=" + source.name());
    }
}
