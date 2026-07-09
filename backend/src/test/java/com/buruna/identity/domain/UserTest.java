package com.buruna.identity.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static User newPendingUser() {
        return User.register(
                Email.of("user@example.com"),
                Username.of("reader"),
                "hash",
                "olá",
                Quota.of(new BigDecimal("2.00")));
    }

    @Test
    void register_startsPendingReaderWithoutTotp() {
        User user = newPendingUser();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getRole()).isEqualTo(Role.READER);
        assertThat(user.isTotpEnabled()).isFalse();
        assertThat(user.getQuotaGb()).isEqualByComparingTo("2.00");
    }

    // ── approve ──────────────────────────────────────────────────────────────

    @Test
    void approve_fromPending_becomesActive() {
        User user = newPendingUser();
        user.approve();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void approve_whenNotPending_throwsNotPending() {
        User user = newPendingUser();
        user.approve();
        assertThatThrownBy(user::approve).isInstanceOf(UserNotPendingException.class);
    }

    // ── reject ───────────────────────────────────────────────────────────────

    @Test
    void reject_fromPending_passesInvariant() {
        User user = newPendingUser();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        user.reject(); // não lança; remoção é responsabilidade da application
    }

    @Test
    void reject_whenNotPending_throwsNotPending() {
        User user = newPendingUser();
        user.approve();
        assertThatThrownBy(user::reject).isInstanceOf(UserNotPendingException.class);
    }

    // ── deactivate ───────────────────────────────────────────────────────────

    @Test
    void deactivate_fromActive_becomesInactive() {
        User user = newPendingUser();
        user.approve();
        user.deactivate();
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void deactivate_whenNotActive_throwsIllegalState() {
        User user = newPendingUser();
        assertThatThrownBy(user::deactivate).isInstanceOf(IllegalStateException.class);
    }

    // ── overrides administrativos ────────────────────────────────────────────

    @Test
    void changeStatus_setsAnyStatus() {
        User user = newPendingUser();
        user.changeStatus(UserStatus.INACTIVE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void changeRole_setsRole() {
        User user = newPendingUser();
        user.changeRole(Role.ADMIN);
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void changeQuota_updatesGigabytes() {
        User user = newPendingUser();
        user.changeQuota(Quota.of(new BigDecimal("10.5")));
        assertThat(user.getQuotaGb()).isEqualByComparingTo("10.5");
    }

    // ── 2FA ──────────────────────────────────────────────────────────────────

    @Test
    void startTotpSetup_storesSecret_withoutEnabling() {
        User user = newPendingUser();
        user.startTotpSetup("SECRET");
        assertThat(user.getTotpSecret()).isEqualTo("SECRET");
        assertThat(user.isTotpEnabled()).isFalse();
    }

    @Test
    void startTotpSetup_whenAlreadyEnabled_throwsIllegalState() {
        User user = newPendingUser();
        user.startTotpSetup("SECRET");
        user.enableTotp();
        assertThatThrownBy(() -> user.startTotpSetup("OTHER"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enableTotp_thenDisable_clearsSecretAndFlag() {
        User user = newPendingUser();
        user.startTotpSetup("SECRET");
        user.enableTotp();
        assertThat(user.isTotpEnabled()).isTrue();

        user.disableTotp();
        assertThat(user.isTotpEnabled()).isFalse();
        assertThat(user.getTotpSecret()).isNull();
    }

    @Test
    void recordLogin_setsLastAccess() {
        User user = newPendingUser();
        var now = java.time.OffsetDateTime.now();
        user.recordLogin(now);
        assertThat(user.getLastAccessAt()).isEqualTo(now);
    }
}
