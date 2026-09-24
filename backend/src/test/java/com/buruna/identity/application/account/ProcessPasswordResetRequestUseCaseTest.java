package com.buruna.identity.application.account;

import com.buruna.identity.domain.Email;
import com.buruna.identity.domain.Quota;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.domain.Username;
import com.buruna.identity.persistence.PasswordResetTokenRepository;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.shared.config.AppProperties;
import com.buruna.shared.notification.EmailDeliveryException;
import com.buruna.shared.notification.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * O Pub/Sub só reentrega a mensagem se o push endpoint responder com erro — logo a
 * exceção de falha no envio do e-mail não pode ser engolida aqui (ADR-42).
 */
@ExtendWith(MockitoExtension.class)
class ProcessPasswordResetRequestUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock EmailService emailService;
    @Mock AppProperties appProperties;

    @Test
    void shouldPropagateEmailDeliveryException_whenEmailServiceFailsToSend() {
        User user = User.register(Email.of("active@buruna.test"), Username.of("activeUser"),
                "hash", "oi", Quota.of(new BigDecimal("2.00")));
        user.changeStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("active@buruna.test")).thenReturn(Optional.of(user));
        when(appProperties.frontendUrl()).thenReturn("https://buruna.test");
        doThrow(new EmailDeliveryException("falha no Resend", new RuntimeException("timeout")))
                .when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString());

        ProcessPasswordResetRequestUseCase useCase = new ProcessPasswordResetRequestUseCase(
                userRepository, passwordResetTokenRepository, emailService, appProperties);

        assertThatThrownBy(() -> useCase.handle("active@buruna.test"))
                .isInstanceOf(EmailDeliveryException.class);
    }
}
