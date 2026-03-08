package com.buruna.auth.service;

import com.buruna.auth.domain.RefreshToken;
import com.buruna.auth.exception.InvalidTokenException;
import com.buruna.auth.repository.RefreshTokenRepository;
import com.buruna.infra.config.AppProperties;
import com.buruna.user.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppProperties appProperties;
    private final SecretKey secretKey;

    public TokenService(RefreshTokenRepository refreshTokenRepository, AppProperties appProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.appProperties = appProperties;
        this.secretKey = Keys.hmacShaKeyFor(appProperties.jwt().secret().getBytes());
    }

    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + appProperties.jwt().expiration() * 1000))
                .signWith(secretKey)
                .compact();
    }

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        refreshTokenRepository.deleteByUserId(user.getId());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(generateSecureToken());
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(appProperties.jwt().refreshTokenExpiration()));

        return refreshTokenRepository.save(refreshToken);
    }

    public UUID validateAccessTokenAndGetUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return UUID.fromString(claims.getSubject());
        } catch (JwtException e) {
            throw new InvalidTokenException();
        }
    }

    @Transactional
    public RefreshToken validateAndRotateRefreshToken(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(InvalidTokenException::new);

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(existing);
            throw new InvalidTokenException();
        }

        User user = existing.getUser();
        refreshTokenRepository.delete(existing);

        RefreshToken rotated = new RefreshToken();
        rotated.setToken(generateSecureToken());
        rotated.setUser(user);
        rotated.setExpiresAt(Instant.now().plusSeconds(appProperties.jwt().refreshTokenExpiration()));

        return refreshTokenRepository.save(rotated);
    }

    @Transactional
    public void deleteRefreshToken(String rawToken) {
        refreshTokenRepository.findByToken(rawToken).ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public void deleteAllUserTokens(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_ENCODER.encodeToString(bytes);
    }
}
