package com.buruna.identity.application.account;

import com.buruna.identity.application.authentication.CaptchaService;
import com.buruna.identity.application.authentication.TokenHash;
import com.buruna.identity.application.authentication.TokenService;
import com.buruna.identity.application.authentication.TotpService;
import com.buruna.identity.domain.AccountOwnershipNotConfirmedException;
import com.buruna.identity.domain.Email;
import com.buruna.identity.domain.InvalidTokenException;
import com.buruna.identity.domain.PasswordResetToken;
import com.buruna.identity.domain.Quota;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserAlreadyExistsException;
import com.buruna.identity.domain.UserNotFoundException;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.domain.Username;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso de gerenciamento de conta: registro, exclusão da própria conta,
 * setup/verificação/desativação de 2FA e reset de senha. A autenticação em si
 * (login/refresh/logout) vive em
 * {@link com.buruna.identity.application.authentication.AuthenticationService}.
 */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final TotpService totpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final StorageClient storageClient;
    private final CaptchaService captchaService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetRequests passwordResetRequests;

    public AccountService(UserRepository userRepository, TokenService tokenService,
                          TotpService totpService, EmailService emailService,
                          PasswordEncoder passwordEncoder, AppProperties appProperties,
                          StorageClient storageClient, CaptchaService captchaService,
                          PasswordResetTokenRepository passwordResetTokenRepository,
                          PasswordResetRequests passwordResetRequests) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.totpService = totpService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.storageClient = storageClient;
        this.captchaService = captchaService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetRequests = passwordResetRequests;
    }

    @Transactional
    public void register(RegisterRequest request, String clientIp) {
        Email email = Email.of(request.email());
        Username username = Username.of(request.username());

        captchaService.verify(request.captchaToken(), clientIp);

        if (userRepository.existsByEmail(email.value())) {
            throw new UserAlreadyExistsException("email");
        }
        if (userRepository.existsByUsername(username.value())) {
            throw new UserAlreadyExistsException("username");
        }

        User user = User.register(email, username, passwordEncoder.encode(request.password()),
                request.presentationMessage(), Quota.of(new BigDecimal("2.00")));

        if (request.avatarBase64() != null && !request.avatarBase64().isBlank()) {
            user.assignAvatar(uploadAvatar(request.avatarBase64()));
        }

        userRepository.save(user);

        List<String> adminEmails = userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)
                .stream().map(User::getEmail).toList();
        emailService.sendNewRegistrationNotification(
                adminEmails.isEmpty() ? List.of(appProperties.adminEmail()) : adminEmails,
                user.getUsername(), user.getEmail()
        );
    }

    /**
     * Confere senha e, com 2FA ativo, o código TOTP antes de uma ação irreversível sobre a
     * própria conta. noRollbackFor mantém gravada a falha de TOTP contada pelo
     * {@link TotpService} (bloqueio após 5 erros).
     */
    @Transactional(noRollbackFor = AccountOwnershipNotConfirmedException.class)
    public void confirmOwnership(UUID userId, String password, String totpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AccountOwnershipNotConfirmedException();
        }
        if (user.isTotpEnabled()) {
            if (totpCode == null || totpCode.isBlank()) {
                throw new AccountOwnershipNotConfirmedException();
            }
            try {
                totpService.verify(user, totpCode);
            } catch (BadCredentialsException e) {
                throw new AccountOwnershipNotConfirmedException();
            }
        }
    }

    /** Anonimiza a conta e apaga os tokens. Devolve o avatar, para a remoção no storage. */
    @Transactional
    public Optional<String> anonymize(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Optional<String> avatar = Optional.ofNullable(user.getAvatarUrl());

        tokenService.deleteAllUserTokens(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        user.anonymize();
        userRepository.save(user);
        return avatar;
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

        // Invariante "2FA já habilitado" mora em User.startTotpSetup.
        String secret = totpService.generateSecret();
        user.startTotpSetup(secret);
        userRepository.save(user);

        String qrUri = totpService.generateQrUri(secret, user.getEmail());
        return new TotpSetupResponse(secret, qrUri);
    }

    // noRollbackFor evita que o rollback padrão de BadCredentialsException
    // desfaça o incremento do contador de falhas de TOTP no agregado.
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public void verify2FA(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getTotpSecret() == null) {
            throw new IllegalStateException("2FA setup not started. Call /auth/2fa/setup first.");
        }

        totpService.verify(user, code);

        user.enableTotp();
        userRepository.save(user);
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public void disable2FA(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isTotpEnabled()) {
            throw new IllegalStateException("2FA is not enabled");
        }

        totpService.verify(user, code);

        user.disableTotp();
        userRepository.save(user);
    }

    public void forgotPassword(String email) {
        passwordResetRequests.submit(email);
    }

    @Transactional(readOnly = true)
    public boolean isResetTokenTotpRequired(String token) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(TokenHash.sha256Hex(token))
                .orElseThrow(InvalidTokenException::new);

        if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException();
        }

        return resetToken.getUser().isTotpEnabled();
    }

    // noRollbackFor evita que o rollback padrão de BadCredentialsException
    // desfaça o incremento do contador de falhas de TOTP no agregado. A verificação
    // do TOTP acontece ANTES de marcar o token como usado ou trocar a senha, então
    // um código errado não consome o token nem muda a senha.
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(TokenHash.sha256Hex(request.token()))
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
            totpService.verify(user, request.totpCode());
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(OffsetDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        tokenService.deleteAllUserTokens(user.getId());
    }

    private String uploadAvatar(String avatarBase64) {
        return StorageUploadHelper.uploadBase64Image(storageClient, avatarBase64, "avatars");
    }
}
