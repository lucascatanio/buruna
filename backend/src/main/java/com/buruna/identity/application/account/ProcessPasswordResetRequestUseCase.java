package com.buruna.identity.application.account;

import com.buruna.identity.application.authentication.TokenHash;
import com.buruna.identity.domain.PasswordResetToken;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.persistence.PasswordResetTokenRepository;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.shared.config.AppProperties;
import com.buruna.shared.notification.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Trabalho de fato do pedido de reset de senha: gera e persiste o token, envia o e-mail.
 * Chamado dentro da requisição do push do Pub/Sub em produção, ou direto pelo endpoint
 * público em dev/local — ver {@link PasswordResetRequests} (ADR-42).
 */
@Service
public class ProcessPasswordResetRequestUseCase {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final AppProperties appProperties;

    public ProcessPasswordResetRequestUseCase(UserRepository userRepository,
                                              PasswordResetTokenRepository passwordResetTokenRepository,
                                              EmailService emailService,
                                              AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.appProperties = appProperties;
    }

    @Transactional
    public void handle(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getStatus() != UserStatus.ACTIVE) return;

            passwordResetTokenRepository.deleteByUserId(user.getId());

            String rawToken = generateSecureToken();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(TokenHash.sha256Hex(rawToken));
            resetToken.setExpiresAt(OffsetDateTime.now().plusHours(1));
            passwordResetTokenRepository.save(resetToken);

            String resetLink = appProperties.frontendUrl() + "/reset-password?token=" + rawToken;
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetLink);
        });
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_ENCODER.encodeToString(bytes);
    }
}
