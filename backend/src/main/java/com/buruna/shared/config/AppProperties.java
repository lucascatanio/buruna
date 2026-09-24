package com.buruna.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        JwtProperties jwt,
        GcsProperties gcs,
        String adminEmail,
        String frontendUrl,
        RateLimitProperties rateLimit,
        SecurityProperties security
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

    /**
     * {@code trustedProxyHops} é o número de proxies reversos confiáveis entre o
     * cliente e este serviço (Cloud Run do frontend + qualquer outro salto de
     * infra). Usado por {@link com.buruna.shared.security.ClientIpResolver} para
     * ler o IP real do cliente no {@code X-Forwarded-For} sem confiar em entradas
     * que o próprio cliente pode forjar (FIND-003).
     */
    public record SecurityProperties(
            int trustedProxyHops
    ) {
    }
}
