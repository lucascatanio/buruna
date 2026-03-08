package com.buruna.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        JwtProperties jwt,
        GcsProperties gcs,
        String adminEmail,
        RateLimitProperties rateLimit
) {
    public record JwtProperties(
            String secret,
            long expiration,
            long refreshTokenExpiration
    ) {
    }

    public record GcsProperties(
            String bucketName,
            String credentialsPath
    ) {
    }

    public record RateLimitProperties(
            int registerPerHour
    ) {
    }
}
