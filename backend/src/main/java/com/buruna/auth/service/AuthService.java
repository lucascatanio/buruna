package com.buruna.auth.service;

import com.buruna.auth.domain.RefreshToken;
import com.buruna.auth.dto.*;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final StorageClient storageClient;

    public AuthService(UserRepository userRepository, TokenService tokenService,
                       EmailService emailService, PasswordEncoder passwordEncoder,
                       AppProperties appProperties, StorageClient storageClient) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.storageClient = storageClient;
    }

    @Transactional
    public void register(RegisterRequest request) {
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
    public TokenResponse login(LoginRequest request) {
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

        user.setLastAccessAt(OffsetDateTime.now());
        userRepository.save(user);

        String accessToken = tokenService.generateAccessToken(user);
        RefreshToken refreshToken = tokenService.createRefreshToken(user);

        return new TokenResponse(accessToken, refreshToken.getToken(), appProperties.jwt().expiration());
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

    private String uploadAvatar(String avatarBase64) {
        return StorageUploadHelper.uploadBase64Image(storageClient, avatarBase64, "avatars");
    }
}
