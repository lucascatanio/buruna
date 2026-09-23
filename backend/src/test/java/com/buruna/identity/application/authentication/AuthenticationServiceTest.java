package com.buruna.identity.application.authentication;

import com.buruna.identity.persistence.UserRepository;
import com.buruna.identity.web.LoginRequest;
import com.buruna.shared.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unidade da regra BAIXA-2 (enumeração de e-mail por tempo de resposta):
 * quando o e-mail não existe, o login precisa gastar o mesmo custo de BCrypt que um
 * login com e-mail existente, comparando a senha recebida contra um hash fictício.
 * Sem isso, {@code passwordEncoder.matches} nunca é chamado para e-mails inexistentes
 * e a resposta fica mensuravelmente mais rápida.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock UserRepository userRepository;
    @Mock TokenService tokenService;
    @Mock TotpService totpService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AppProperties appProperties;

    @Test
    void shouldCompareAgainstDummyHash_whenEmailDoesNotExist() {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$dummyHashUsedOnlyForTiming");
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        AuthenticationService service = new AuthenticationService(
                userRepository, tokenService, totpService, passwordEncoder, appProperties);

        assertThatThrownBy(() -> service.login(new LoginRequest("ninguem@buruna.test", "qualquer-senha")))
                .isInstanceOf(BadCredentialsException.class);

        // A correção precisa gastar o mesmo custo de BCrypt de um login real —
        // sem essa chamada, a ausência de e-mail responde mais rápido e vaza a
        // existência da conta.
        verify(passwordEncoder).matches(anyString(), anyString());
    }
}
