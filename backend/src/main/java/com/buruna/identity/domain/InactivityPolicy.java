package com.buruna.identity.domain;

import java.time.OffsetDateTime;

/**
 * Política pura de inatividade: dados o último acesso e o instante atual, decide se o
 * usuário deve ser deixado como está, avisado ou desativado. Domínio PURO — sem Spring,
 * sem repositório, sem I/O: recebe os dois instantes e decide.
 *
 * <p>Limiares (dias de inatividade), EXCLUSIVOS na borda — espelham o {@code isBefore}
 * do job legado, preservando comportamento idêntico:
 * <ul>
 *   <li>inatividade &gt; 90 dias &rarr; {@link InactivityDecision#DEACTIVATE}
 *       (exatamente 90 dias &rarr; WARN)</li>
 *   <li>inatividade &gt; 75 dias &rarr; {@link InactivityDecision#WARN}
 *       (exatamente 75 dias &rarr; NONE)</li>
 *   <li>caso contrário &rarr; {@link InactivityDecision#NONE}</li>
 * </ul>
 *
 * <p>A substituição de {@code lastAccessAt} nulo por {@code createdAt} é responsabilidade do
 * chamador (o job de inatividade), exatamente como hoje — a policy não conhece {@code createdAt}.
 * Aqui, um {@code lastAccessAt} nulo é tratado defensivamente como {@link InactivityDecision#NONE}
 * (sem dado de atividade, não age).
 */
public class InactivityPolicy {

    static final int WARNING_THRESHOLD_DAYS = 75;
    static final int DEACTIVATION_THRESHOLD_DAYS = 90;

    public InactivityDecision decide(OffsetDateTime lastAccessAt, OffsetDateTime now) {
        if (lastAccessAt == null) {
            return InactivityDecision.NONE;
        }
        if (lastAccessAt.isBefore(now.minusDays(DEACTIVATION_THRESHOLD_DAYS))) {
            return InactivityDecision.DEACTIVATE;
        }
        if (lastAccessAt.isBefore(now.minusDays(WARNING_THRESHOLD_DAYS))) {
            return InactivityDecision.WARN;
        }
        return InactivityDecision.NONE;
    }
}
