package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.SourceName;

/**
 * Pede uma execução do executor de fonte (ADR-48), depois do commit de quem chama. Em produção
 * dispara o Cloud Run Job; no profile local roda na própria thread.
 */
public interface SourcingTrigger {

    /** Uma rodada do executor de {@code source}: cada fonte tem o seu lease e a sua execução. */
    void requestRun(SourceName source);
}
