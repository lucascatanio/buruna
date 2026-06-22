package com.buruna.identity.application.account;

import com.buruna.identity.application.authentication.CaptchaService;
import com.buruna.identity.application.authentication.TokenService;
import com.buruna.identity.application.authentication.TotpService;
import com.buruna.identity.domain.InvalidTokenException;
import com.buruna.identity.domain.PasswordResetToken;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserAlreadyExistsException;
import com.buruna.identity.domain.UserNotFoundException;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.persistence.PasswordResetTokenRepository;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.identity.web.RegisterRequest;
import com.buruna.identity.web.ResetPasswordRequest;
import com.buruna.identity.web.TotpSetupResponse;
import com.buruna.shared.config.AppProperties;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.storage.StorageClient;
import com.buruna.shared.storage.StorageUploadHelper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de gerenciamento de conta: registro, exclusão da própria conta,
 * setup/verificação/desativação de 2FA e reset de senha. A autenticação em si
 * (login/refresh/logout) vive em
 * {@link com.buruna.identity.application.authentication.AuthenticationService}.
 */
@Service
public class AccountService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final TotpService totpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final StorageClient storageClient;
    private final CaptchaService captchaService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public AccountService(UserRepository userRepository, TokenService tokenService,
                          TotpService totpService, EmailService emailService,
                          PasswordEncoder passwordEncoder, AppProperties appProperties,
                          StorageClient storageClient, CaptchaService captchaService,
                          PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.totpService = totpService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.storageClient = storageClient;
        this.captchaService = captchaService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Transactional
    public void register(RegisterRequest request, String clientIp) {
        captchaService.verify(request.captchaToken(), clientIp);

        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("email");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("username");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPresentationMessage(request.presentationMessage());
        user.setRole(Role.READER);
        user.setStatus(UserStatus.PENDING);
        user.setQuotaGb(new BigDecimal("2.00"));

        if (request.avatarBase64() != null && !request.avatarBase64().isBlank()) {
            String avatarObjectName = uploadAvatar(request.avatarBase64());
            user.setAvatarUrl(avatarObjectName);
        }

        userRepository.save(user);

        List<User> admins = userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
        if (admins.isEmpty()) {
            emailService.sendNewRegistrationNotification(
                    appProperties.adminEmail(), user.getUsername(), user.getEmail()
            );
        } else {
            for (User admin : admins) {
                emailService.sendNewRegistrationNotification(
                        admin.getEmail(), user.getUsername(), user.getEmail()
                );
            }
        }
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        tokenService.deleteAllUserTokens(userId);
        userRepository.delete(user);
    }

    public boolean is2FAEnabled(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return user.isTotpEnabled();
    }

    @Transactional
    public TotpSetupResponse setup2FA(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.isTotpEnabled()) {
            throw new IllegalStateException("2FA is already enabled");
        }

        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        userRepository.save(user);

        String qrUri = totpService.generateQrUri(secret, user.getEmail());
        return new TotpSetupResponse(secret, qrUri);
    }

    @Transactional
    public void verify2FA(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getTotpSecret() == null) {
            throw new IllegalStateException("2FA setup not started. Call /auth/2fa/setup first.");
        }

        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new BadCredentialsException("Invalid TOTP code");
        }

        user.setTotpEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void disable2FA(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isTotpEnabled()) {
            throw new IllegalStateException("2FA is not enabled");
        }

        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new BadCredentialsException("Invalid TOTP code");
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getStatus() != UserStatus.ACTIVE) return;

            passwordResetTokenRepository.deleteByUserId(user.getId());

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(generateSecureToken());
            resetToken.setExpiresAt(OffsetDateTime.now().plusHours(1));
            passwordResetTokenRepository.save(resetToken);

            String resetLink = appProperties.frontendUrl() + "/reset-password?token=" + resetToken.getToken();
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetLink);
        });
    }

    @Transactional(readOnly = true)
    public boolean isResetTokenTotpRequired(String token) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(InvalidTokenException::new);

        if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException();
        }

        return resetToken.getUser().isTotpEnabled();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.token())
                .orElseThrow(InvalidTokenException::new);

        if (resetToken.getUsedAt() != null) {
            throw new InvalidTokenException();
        }
        if (resetToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException();
        }

        User user = resetToken.getUser();

        if (user.isTotpEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw new BadCredentialsException("TOTP code is required");
            }
            if (!totpService.verifyCode(user.getTotpSecret(), request.totpCode())) {
                throw new BadCredentialsException("Invalid TOTP code");
            }
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(OffsetDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        tokenService.deleteAllUserTokens(user.getId());
    }

    private String uploadAvatar(String avatarBase64) {
        return StorageUploadHelper.uploadBase64Image(storageClient, avatarBase64, "avatars");
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_ENCODER.encodeToString(bytes);
    }
}
