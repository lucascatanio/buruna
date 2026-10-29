package com.buruna.sourcing.application;

import org.springframework.stereotype.Service;

/**
 * Pede uma rodada do executor sem importar nada: retoma downloads pendentes (Job que caiu no
 * meio, disparo que falhou). Se outra rodada estiver rodando, esta espera o lease ou desiste,
 * e a que roda baixa o que estiver pendente.
 */
@Service
public class RequestSourcingRunUseCase {

    private final SourcingTrigger trigger;

    public RequestSourcingRunUseCase(SourcingTrigger trigger) {
        this.trigger = trigger;
    }

    public void handle() {
        trigger.requestRun();
    }
}
