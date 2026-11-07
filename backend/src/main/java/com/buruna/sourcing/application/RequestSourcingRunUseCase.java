package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.TrackedWorkStatus;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pede uma rodada do executor sem importar nada: retoma downloads pendentes (Job que caiu no
 * meio, disparo que falhou). Se outra rodada estiver rodando, esta espera o lease ou desiste,
 * e a que roda baixa o que estiver pendente.
 */
@Service
public class RequestSourcingRunUseCase {

    private final SourcingTrigger trigger;
    private final TrackedWorkRepository trackedWorkRepository;

    public RequestSourcingRunUseCase(SourcingTrigger trigger, TrackedWorkRepository trackedWorkRepository) {
        this.trigger = trigger;
        this.trackedWorkRepository = trackedWorkRepository;
    }

    /** Uma rodada por fonte que tem obra ativa: cada uma tem o seu lease e a sua execução. */
    @Transactional(readOnly = true)
    public void handle() {
        for (SourceName source : SourceName.values()) {
            if (trackedWorkRepository.existsBySourceAndStatus(source, TrackedWorkStatus.ACTIVE)) {
                trigger.requestRun(source);
            }
        }
    }
}
