package com.buruna.shared.security;

import com.buruna.shared.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.List;

/**
 * Só aceita requisição que passou pelo nginx do frontend, que acrescenta o header
 * {@code X-Proxy-Secret}. Sem isso, quem chama o {@code run.app} do backend direto
 * controla o {@code X-Forwarded-For} lido pelo {@link ClientIpResolver} e burla o rate
 * limit (ADR-43).
 *
 * <p>Desligado quando {@code app.proxy.secret} está vazio (dev local e testes não usam
 * nginx); nesse caso loga um WARN no startup. Fica antes do {@link RateLimitFilter}.
 */
@Component
public class ProxySecretFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Proxy-Secret";

    private static final Logger log = LoggerFactory.getLogger(ProxySecretFilter.class);

    /**
     * Chamadores diretos do Google, com autenticação própria: push do Pub/Sub (OIDC) e
     * Cloud Scheduler (X-Job-Secret).
     */
    private static final List<String> EXEMPT_PREFIXES = List.of("/internal/pubsub/", "/admin/jobs/");
    /** Probe do Cloud Run: bate no container sem passar pelo nginx e só devolve {"status":"UP"}. */
    private static final String HEALTH_PATH = "/health";

    private final byte[] secret;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProxySecretFilter(@Value("${app.proxy.secret:}") String secret, ObjectMapper objectMapper, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
        this.clock = clock;
        if (!isEnabled()) {
            log.warn("app.proxy.secret vazio: o backend aceita requisições que não passaram pelo nginx "
                    + "(rate limit burlável via X-Forwarded-For). Defina APP_PROXY_SECRET em produção.");
        }
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!isEnabled() || isExempt(request) || hasValidSecret(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ErrorResponse body = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Forbidden",
                request.getRequestURI(),
                clock.instant().toString());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }

    private boolean isEnabled() {
        return secret.length > 0;
    }

    private boolean hasValidSecret(HttpServletRequest request) {
        String provided = request.getHeader(HEADER);
        return provided != null
                && MessageDigest.isEqual(secret, provided.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isExempt(HttpServletRequest request) {
        // o context-path (/api) faz parte do requestURI; as exceções são relativas a ele
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals(HEALTH_PATH) || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
