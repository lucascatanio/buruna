package com.buruna.infra.security;

import com.buruna.infra.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String REGISTER_SUFFIX = "/auth/register";
    private static final String LOGIN_SUFFIX = "/auth/login";
    private static final long WINDOW_MS = 3_600_000L;

    private final ConcurrentHashMap<String, RateEntry> attempts = new ConcurrentHashMap<>();
    private final Map<String, Integer> limits;

    public RateLimitFilter(AppProperties appProperties) {
        this.limits = Map.of(
                REGISTER_SUFFIX, appProperties.rateLimit().registerPerHour(),
                LOGIN_SUFFIX, appProperties.rateLimit().loginPerHour()
        );
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!request.getMethod().equalsIgnoreCase("POST")) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String matchedSuffix = null;
        Integer maxAttempts = null;
        for (Map.Entry<String, Integer> limitEntry : limits.entrySet()) {
            if (uri.endsWith(limitEntry.getKey())) {
                matchedSuffix = limitEntry.getKey();
                maxAttempts = limitEntry.getValue();
                break;
            }
        }

        if (maxAttempts == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        String key = matchedSuffix + ":" + ip;
        long now = Instant.now().toEpochMilli();

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

    @Scheduled(fixedDelay = 3_600_000L)
    public void evictExpiredEntries() {
        long now = Instant.now().toEpochMilli();
        attempts.entrySet().removeIf(e -> now - e.getValue().windowStart() > WINDOW_MS);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private record RateEntry(long windowStart, AtomicInteger count) {
    }
}
