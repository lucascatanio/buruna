package com.buruna.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        JwtProperties jwt,
        GcsProperties gcs,
        String adminEmail,
        String frontendUrl,
        RateLimitProperties rateLimit,
        SecurityProperties security,
        AuthProperties auth
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
            int registerPerHour,
            int loginPerHour,
            int feedbackPerHour,
            int forgotPasswordPerHour
    ) {
    }

    public record SecurityProperties(
            int trustedProxyHops
    ) {
    }

    public record AuthProperties(
            boolean cookieSecure
    ) {
    }
}
