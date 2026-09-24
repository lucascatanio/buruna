package com.buruna.identity.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    // ── canAuthenticate ──────────────────────────────────────────────────────

    @Test
    void canAuthenticate_whenPending_returnsFalse() {
        User user = newPendingUser();
        assertThat(user.canAuthenticate()).isFalse();
    }

    @Test
    void canAuthenticate_whenActive_returnsTrue() {
        User user = newPendingUser();
        user.approve();
        assertThat(user.canAuthenticate()).isTrue();
    }

    @Test
    void canAuthenticate_whenInactive_returnsFalse() {
        User user = newPendingUser();
        user.approve();
        user.deactivate();
        assertThat(user.canAuthenticate()).isFalse();
    }

    // ── TOTP: bloqueio por força bruta e replay ───────────────────────────────

    @Test
    void registerTotpFailure_locksUser_onFifthConsecutiveFailure() {
        User user = newPendingUser();
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        for (int i = 0; i < 4; i++) {
            user.registerTotpFailure(now);
        }
        assertThatCode(() -> user.assertTotpNotLocked(now)).doesNotThrowAnyException();

        user.registerTotpFailure(now); // 5ª falha consecutiva
        assertThatThrownBy(() -> user.assertTotpNotLocked(now))
                .isInstanceOf(TotpLockedException.class);
    }

    @Test
    void assertTotpNotLocked_unlocksAgain_after15MinutesPass() {
        User user = newPendingUser();
        OffsetDateTime lockedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            user.registerTotpFailure(lockedAt);
        }

        assertThatThrownBy(() -> user.assertTotpNotLocked(lockedAt.plusMinutes(14)))
                .isInstanceOf(TotpLockedException.class);

        assertThatCode(() -> user.assertTotpNotLocked(lockedAt.plusMinutes(15).plusSeconds(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptTotpStep_rejectsReplay_whenStepAlreadyUsedOrOlder() {
        User user = newPendingUser();
        user.acceptTotpStep(100L);

        assertThatThrownBy(() -> user.acceptTotpStep(100L))
                .isInstanceOf(TotpReplayException.class);
        assertThatThrownBy(() -> user.acceptTotpStep(99L))
                .isInstanceOf(TotpReplayException.class);
    }

    @Test
    void acceptTotpStep_resetsFailedAttempts_onSuccess() {
        User user = newPendingUser();
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        user.registerTotpFailure(now);
        user.registerTotpFailure(now);
        user.registerTotpFailure(now);
        user.registerTotpFailure(now);

        user.acceptTotpStep(50L);

        // O contador zerou: mais 4 falhas (não 1) não bastam para bloquear de novo.
        user.registerTotpFailure(now);
        user.registerTotpFailure(now);
        user.registerTotpFailure(now);
        user.registerTotpFailure(now);
        assertThatCode(() -> user.assertTotpNotLocked(now)).doesNotThrowAnyException();
    }
}
