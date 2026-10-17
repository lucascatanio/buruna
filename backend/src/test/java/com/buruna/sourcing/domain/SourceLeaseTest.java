package com.buruna.sourcing.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SourceLeaseTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-17T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);

    static SourceLease lease() throws Exception {
        Constructor<SourceLease> constructor = SourceLease.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @Test
    void shouldRefuseOtherHolder_whenLeaseIsHeld() throws Exception {
        SourceLease lease = lease();
        lease.tryAcquire("a", NOW, TTL);

        assertThat(lease.tryAcquire("b", NOW.plusMinutes(5), TTL)).isFalse();
        assertThat(lease.getHolder()).isEqualTo("a");
    }

    @Test
    void shouldHandOver_whenLeaseExpired() throws Exception {
        SourceLease lease = lease();
        lease.tryAcquire("a", NOW, TTL);

        assertThat(lease.tryAcquire("b", NOW.plusMinutes(10), TTL)).isTrue();
        assertThat(lease.getHolder()).isEqualTo("b");
    }

    @Test
    void shouldRenew_whenSameHolderAsksAgain() throws Exception {
        SourceLease lease = lease();
        lease.tryAcquire("a", NOW, TTL);

        assertThat(lease.tryAcquire("a", NOW.plusMinutes(9), TTL)).isTrue();
        assertThat(lease.getLockedUntil()).isEqualTo(NOW.plusMinutes(19));
    }

    @Test
    void shouldIgnoreRelease_whenNotTheHolder() throws Exception {
        SourceLease lease = lease();
        lease.tryAcquire("a", NOW, TTL);

        lease.release("b");

        assertThat(lease.getHolder()).isEqualTo("a");
    }
}
