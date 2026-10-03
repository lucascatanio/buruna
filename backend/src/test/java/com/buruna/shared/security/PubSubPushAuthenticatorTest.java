package com.buruna.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PubSubPushAuthenticatorTest {

    private static Environment environmentAcceptingLocal(boolean localActive) {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(localActive);
        return environment;
    }

    private static PubSubPushAuthenticator configuredAuthenticator() {
        return new PubSubPushAuthenticator("audience", "pubsub-push-invoker@buruna.iam.gserviceaccount.com",
                environmentAcceptingLocal(false));
    }

    @Test
    void shouldThrowIllegalState_whenNotConfigured_andLocalProfileNotActive() {
        Environment environment = environmentAcceptingLocal(false);

        assertThatThrownBy(() -> new PubSubPushAuthenticator("", "", environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotThrow_whenNotConfigured_andLocalProfileActive() {
        Environment environment = environmentAcceptingLocal(true);

        assertThatCode(() -> new PubSubPushAuthenticator("", "", environment))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWithBadCredentials_whenAuthorizationHeaderMissing() {
        PubSubPushAuthenticator authenticator = configuredAuthenticator();

        assertThatThrownBy(() -> authenticator.verify(null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectWithBadCredentials_whenHeaderWithoutBearerPrefix() {
        PubSubPushAuthenticator authenticator = configuredAuthenticator();

        assertThatThrownBy(() -> authenticator.verify("token-sem-prefixo"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectWithBadCredentials_whenTokenMalformed() {
        PubSubPushAuthenticator authenticator = configuredAuthenticator();

        assertThatThrownBy(() -> authenticator.verify("Bearer nao-eh-um-jwt-valido"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectWithBadCredentials_whenNotConfigured_andLocalProfileActive() {
        // Sem app.pubsub.push-audience/push-service-account, o verifier fica null mesmo
        // com o profile local ativo (só o construtor deixa de falhar): todo push é recusado.
        PubSubPushAuthenticator authenticator = new PubSubPushAuthenticator("", "", environmentAcceptingLocal(true));

        assertThatThrownBy(() -> authenticator.verify("Bearer qualquer-coisa"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
