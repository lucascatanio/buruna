package com.buruna.identity.application.authentication;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaptchaServiceTest {

    private static Environment environmentAcceptingLocal(boolean localActive) {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(localActive);
        return environment;
    }

    @Test
    void shouldThrowIllegalState_whenSecretBlank_andLocalProfileNotActive() {
        Environment environment = environmentAcceptingLocal(false);

        assertThatThrownBy(() -> new CaptchaService("", environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowIllegalState_whenSecretNull_andLocalProfileNotActive() {
        Environment environment = environmentAcceptingLocal(false);

        assertThatThrownBy(() -> new CaptchaService(null, environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotThrow_whenSecretBlank_andLocalProfileActive() {
        Environment environment = environmentAcceptingLocal(true);

        assertThatCode(() -> new CaptchaService("", environment)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrow_whenSecretConfigured_regardlessOfProfile() {
        Environment environment = environmentAcceptingLocal(false);

        assertThatCode(() -> new CaptchaService("um-segredo-hcaptcha", environment))
                .doesNotThrowAnyException();
    }
}
