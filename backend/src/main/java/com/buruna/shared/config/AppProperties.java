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

    /**
     * {@code cookieSecure} controla o atributo {@code Secure} do cookie
     * {@code buruna_refresh} (ADR-41). Precisa ser {@code false} em
     * {@code application-local.yml} porque o dev local roda em HTTP puro — um
     * cookie {@code Secure} nunca seria enviado de volta pelo navegador nesse caso.
     */
    public record AuthProperties(
            boolean cookieSecure
    ) {
    }
}
