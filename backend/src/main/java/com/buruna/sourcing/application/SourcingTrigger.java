package com.buruna.sourcing.application;

/**
 * Pede uma execução do executor de fonte (ADR-48), depois do commit de quem chama. Em produção
 * dispara o Cloud Run Job; no profile local roda na própria thread.
 */
public interface SourcingTrigger {

    void requestRun();
}
