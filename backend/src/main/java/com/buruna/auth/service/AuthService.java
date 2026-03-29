package com.buruna.auth.service;

import com.buruna.auth.domain.PasswordResetToken;
import com.buruna.auth.domain.RefreshToken;
import com.buruna.auth.dto.*;
import com.buruna.auth.exception.InvalidTokenException;
import com.buruna.auth.repository.PasswordResetTokenRepository;
import com.buruna.infra.config.AppProperties;
import com.buruna.infra.notification.EmailService;
import com.buruna.infra.storage.StorageClient;
import com.buruna.infra.storage.StorageUploadHelper;
import com.buruna.user.domain.Role;
import com.buruna.user.domain.User;
import com.buruna.user.domain.UserStatus;
import com.buruna.user.exception.UserAlreadyExistsException;
import com.buruna.user.exception.UserNotActiveException;
import com.buruna.user.exception.UserNotFoundException;
import com.buruna.user.repository.UserRepository;
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

@Service
public class AuthService {

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

    public AuthService(UserRepository userRepository, TokenService tokenService,
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
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new UserNotActiveException("Your account is pending approval");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new UserNotActiveException("Your account has been deactivated");
        }

        if (user.isTotpEnabled()) {
            String tempToken = tokenService.generateTempToken(user);
            return LoginResponse.requires2FA(tempToken);
        }

        return issueTokens(user);
    }

    @Transactional
    public LoginResponse authenticate2FA(TotpAuthenticateRequest request) {
        UUID userId = tokenService.validateTempTokenAndGetUserId(request.tempToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            throw new BadCredentialsException("2FA is not enabled for this account");
        }

        if (!totpService.verifyCode(user.getTotpSecret(), request.totpCode())) {
            throw new BadCredentialsException("Invalid TOTP code");
        }

        return issueTokens(user);
    }

    private LoginResponse issueTokens(User user) {
        user.setLastAccessAt(OffsetDateTime.now());
        userRepository.save(user);

        String accessToken = tokenService.generateAccessToken(user);
        RefreshToken refreshToken = tokenService.createRefreshToken(user);

        return LoginResponse.authenticated(accessToken, refreshToken.getToken(), appProperties.jwt().expiration());
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken rotated = tokenService.validateAndRotateRefreshToken(rawRefreshToken);
        User user = rotated.getUser();

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserNotActiveException("Your account is not active");
        }

        return new TokenResponse(
                tokenService.generateAccessToken(user),
                rotated.getToken(),
                appProperties.jwt().expiration()
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        tokenService.deleteRefreshToken(rawRefreshToken);
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
