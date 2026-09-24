package com.buruna.identity.application.authentication;

import com.buruna.identity.domain.RefreshToken;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserNotActiveException;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.identity.web.LoginRequest;
import com.buruna.identity.web.LoginResponse;
import com.buruna.identity.web.TokenResponse;
import com.buruna.identity.web.TotpAuthenticateRequest;
import com.buruna.shared.config.AppProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Casos de uso de autenticação: login (com/sem 2FA), troca de refresh token e logout.
 * O gerenciamento de conta (registro, reset de senha, setup de 2FA) vive em
 * {@link com.buruna.identity.application.account.AccountService}.
 */
@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    /**
     * Hash fictício gerado uma vez, usado quando o e-mail não existe. Mantém o custo do
     * BCrypt igual ao de um login real e evita que a diferença de tempo de resposta
     * revele se um e-mail está cadastrado (enumeração de e-mails).
     */
    private final String dummyPasswordHash;

    public AuthenticationService(UserRepository userRepository, TokenService tokenService,
                                 TotpService totpService, PasswordEncoder passwordEncoder,
                                 AppProperties appProperties) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.totpService = totpService;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new BadCredentialsException("Invalid credentials");
        }

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

    // FIND-004: noRollbackFor evita que o rollback padrão de BadCredentialsException
    // desfaça o incremento do contador de falhas de TOTP no agregado.
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponse authenticate2FA(TotpAuthenticateRequest request) {
        UUID userId = tokenService.validateTempTokenAndGetUserId(request.tempToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            throw new BadCredentialsException("2FA is not enabled for this account");
        }

        totpService.verify(user, request.totpCode());

        return issueTokens(user);
    }

    private LoginResponse issueTokens(User user) {
        user.recordLogin(OffsetDateTime.now());
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
}
