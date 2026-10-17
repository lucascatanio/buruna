package com.buruna.sourcing.application;

import com.buruna.shared.transaction.AfterCommit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Profile local: a rodada acontece na própria requisição, depois do commit. Uma obra longa
 * segura a requisição até terminar, o que só é aceitável em desenvolvimento.
 */
@Component
@Profile("local")
public class InlineSourcingTrigger implements SourcingTrigger {

    private final SourcingRunner runner;

    public InlineSourcingTrigger(SourcingRunner runner) {
        this.runner = runner;
    }

    @Override
    public void requestRun() {
        AfterCommit.run(() -> runner.run("inline-" + UUID.randomUUID()));
    }
}
