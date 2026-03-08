package com.buruna.infra.security;

import com.buruna.infra.config.AppProperties;
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
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String REGISTER_SUFFIX = "/auth/register";
    private static final long WINDOW_MS = 3_600_000L;

    private final ConcurrentHashMap<String, RateEntry> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;

    public RateLimitFilter(AppProperties appProperties) {
        this.maxAttempts = appProperties.rateLimit().registerPerHour();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        boolean isRegister = request.getRequestURI().endsWith(REGISTER_SUFFIX)
                && request.getMethod().equalsIgnoreCase("POST");

        if (!isRegister) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        long now = Instant.now().toEpochMilli();

        RateEntry entry = attempts.compute(ip, (key, existing) -> {
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
                    {"status":429,"error":"Too Many Requests","message":"Too many registration attempts. Try again later."}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private record RateEntry(long windowStart, AtomicInteger count) {
    }
}
