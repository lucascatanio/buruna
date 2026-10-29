package com.buruna.sourcing.application;

/** Argumento que pede ao Job de ingest uma rodada do executor de fonte. */
public final class SourcingJobArgs {

    public static final String RUN_OPTION = "sourcing.run";
    /** Passado pelo Cloud Scheduler a cada 3 h (ADR-49). */
    public static final String SYNC_OPTION = "sourcing.sync";

    private SourcingJobArgs() {
    }

    static String run() {
        return "--" + RUN_OPTION + "=true";
    }
}
