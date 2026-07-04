package com.buruna.identity.domain;

/**
 * Resultado da {@link InactivityPolicy}: nada a fazer, avisar ou desativar o usuário.
 */
public enum InactivityDecision {
    NONE, WARN, DEACTIVATE
}
