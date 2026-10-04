package com.buruna.shared.security;

import com.buruna.shared.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String REGISTER_SUFFIX = "/auth/register";
    private static final String LOGIN_SUFFIX = "/auth/login";
    private static final String FEEDBACK_SUFFIX = "/feedback";
    private static final String FORGOT_PASSWORD_SUFFIX = "/auth/password/forgot";
    private static final String DELETE_ACCOUNT_SUFFIX = "/auth/account";
    private static final long WINDOW_MS = 3_600_000L;

    private final ConcurrentHashMap<String, RateEntry> attempts = new ConcurrentHashMap<>();
    private final List<Limit> limits;
    private final ClientIpResolver clientIpResolver;
    private final Clock clock;
    // 0 faz a primeira requisição limitada varrer o mapa (vazio) e marcar o relógio
    private final AtomicLong lastEviction = new AtomicLong(0);

    public RateLimitFilter(AppProperties appProperties, ClientIpResolver clientIpResolver, Clock clock) {
        AppProperties.RateLimitProperties rateLimit = appProperties.rateLimit();
        this.limits = List.of(
                new Limit("POST", REGISTER_SUFFIX, rateLimit.registerPerHour()),
                new Limit("POST", LOGIN_SUFFIX, rateLimit.loginPerHour()),
                new Limit("POST", FEEDBACK_SUFFIX, rateLimit.feedbackPerHour()),
                new Limit("POST", FORGOT_PASSWORD_SUFFIX, rateLimit.forgotPasswordPerHour()),
                // confere a senha: sem limite, um access token vazado vira oráculo de senha
                new Limit("DELETE", DELETE_ACCOUNT_SUFFIX, rateLimit.deleteAccountPerHour())
        );
        this.clientIpResolver = clientIpResolver;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();
        Limit matched = limits.stream()
                .filter(l -> l.method().equalsIgnoreCase(method) && uri.endsWith(l.suffix()))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            filterChain.doFilter(request, response);
            return;
        }
        int maxAttempts = matched.perHour();

        String ip = clientIpResolver.resolve(request);
        String key = matched.suffix() + ":" + ip;
        long now = clock.instant().toEpochMilli();
        evictExpiredEntriesIfDue(now);

        RateEntry entry = attempts.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart() > WINDOW_MS) {
                return new RateEntry(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (entry.count().get() > maxAttempts) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":429,"error":"Too Many Requests","message":"Too many attempts. Try again later."}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Limpa as entradas vencidas dentro de uma requisição, no máximo uma vez por janela. Não
     * usa {@code @Scheduled}: no Cloud Run com cpu-throttling a CPU fica cortada fora de
     * requisição (ADR-03), e o Cloud Scheduler não serve porque o mapa vive na memória de
     * cada instância. O compareAndSet garante que só uma thread varre.
     */
    private void evictExpiredEntriesIfDue(long now) {
        long last = lastEviction.get();
        if (now - last < WINDOW_MS || !lastEviction.compareAndSet(last, now)) {
            return;
        }
        attempts.entrySet().removeIf(e -> now - e.getValue().windowStart() > WINDOW_MS);
    }

    int trackedKeys() {
        return attempts.size();
    }

    private record RateEntry(long windowStart, AtomicInteger count) {
    }

    private record Limit(String method, String suffix, int perHour) {
    }
}
