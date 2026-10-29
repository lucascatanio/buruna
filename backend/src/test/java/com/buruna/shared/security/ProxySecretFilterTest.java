package com.buruna.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ProxySecretFilterTest {

    private static final String SECRET = "segredo-do-proxy";

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC);

    private ProxySecretFilter filterWith(String secret) {
        return new ProxySecretFilter(secret, new ObjectMapper(), clock);
    }

    private MockHttpServletResponse call(ProxySecretFilter filter, String uri, String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContextPath("/api");
        if (headerValue != null) {
            request.addHeader("X-Proxy-Secret", headerValue);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void shouldPass_whenHeaderMatchesSecret() throws Exception {
        MockHttpServletResponse response = call(filterWith(SECRET), "/api/auth/login", SECRET);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturn403WithErrorResponse_whenHeaderIsMissing() throws Exception {
        MockHttpServletResponse response = call(filterWith(SECRET), "/api/auth/login", null);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":403", "\"error\":\"Forbidden\"", "\"path\":\"/api/auth/login\"",
                        "\"timestamp\":\"2026-10-08T12:00:00Z\"");
    }

    @Test
    void shouldReturn403_whenHeaderIsWrong() throws Exception {
        MockHttpServletResponse response = call(filterWith(SECRET), "/api/auth/login", "outro");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldPassEverything_whenSecretIsEmpty() throws Exception {
        MockHttpServletResponse response = call(filterWith(""), "/api/auth/login", null);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldPassWithoutHeader_whenPathIsPubSubPush() throws Exception {
        MockHttpServletResponse response = call(filterWith(SECRET), "/api/internal/pubsub/password-reset", null);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldPassWithoutHeader_whenPathIsSchedulerJob() throws Exception {
        MockHttpServletResponse response = call(filterWith(SECRET), "/api/admin/jobs/inactivity", null);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldPassWithoutHeader_whenPathIsHealth() throws Exception {
        MockHttpServletResponse response = call(filterWith(SECRET), "/api/health", null);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturn403_whenExemptPrefixAppearsOnlyInsideAnotherPath() throws Exception {
        MockHttpServletResponse response = call(filterWith(SECRET), "/api/works/admin/jobs/x", null);

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
