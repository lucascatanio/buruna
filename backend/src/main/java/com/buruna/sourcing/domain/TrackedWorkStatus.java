package com.buruna.sourcing.domain;

/** ACTIVE: o executor baixa capítulos novos. PAUSED: nada é baixado até o ADM retomar. */
public enum TrackedWorkStatus {
    ACTIVE,
    PAUSED
}
