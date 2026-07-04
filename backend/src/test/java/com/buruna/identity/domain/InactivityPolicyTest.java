package com.buruna.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InactivityPolicyTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 4, 2, 0, 0, 0, ZoneOffset.UTC);

    private final InactivityPolicy policy = new InactivityPolicy();

    @Test
    void shouldReturnNone_whenInactive74Days() {
        assertThat(policy.decide(NOW.minusDays(74), NOW)).isEqualTo(InactivityDecision.NONE);
    }

    @Test
    void shouldReturnNone_whenInactiveExactly75Days() {
        assertThat(policy.decide(NOW.minusDays(75), NOW)).isEqualTo(InactivityDecision.NONE);
    }

    @Test
    void shouldWarn_whenInactive76Days() {
        assertThat(policy.decide(NOW.minusDays(76), NOW)).isEqualTo(InactivityDecision.WARN);
    }

    @Test
    void shouldWarn_whenInactive89Days() {
        assertThat(policy.decide(NOW.minusDays(89), NOW)).isEqualTo(InactivityDecision.WARN);
    }

    @Test
    void shouldWarn_whenInactiveExactly90Days() {
        assertThat(policy.decide(NOW.minusDays(90), NOW)).isEqualTo(InactivityDecision.WARN);
    }

    @Test
    void shouldDeactivate_whenInactive91Days() {
        assertThat(policy.decide(NOW.minusDays(91), NOW)).isEqualTo(InactivityDecision.DEACTIVATE);
    }

    @Test
    void shouldReturnNone_whenLastAccessIsNull() {
        assertThat(policy.decide(null, NOW)).isEqualTo(InactivityDecision.NONE);
    }
}
