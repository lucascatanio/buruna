package com.buruna.shared.security;

import com.buruna.shared.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private static final Instant T0 = Instant.parse("2026-10-03T12:00:00Z");

    private Clock clock;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        AppProperties appProperties = new AppProperties(
                null, null, null, null,
                new AppProperties.RateLimitProperties(5, 10, 5, 3, 5),
                new AppProperties.SecurityProperties(1), null);
        filter = new RateLimitFilter(appProperties, new ClientIpResolver(appProperties), clock);
    }

    private int loginFrom(String ip, Duration afterT0) throws Exception {
        when(clock.instant()).thenReturn(T0.plus(afterT0));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        request.addHeader("X-Forwarded-For", ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void shouldEvictExpiredEntries_whenRequestArrivesAfterWindow() throws Exception {
        loginFrom("1.1.1.1", Duration.ZERO);
        loginFrom("2.2.2.2", Duration.ofMinutes(30));

        loginFrom("3.3.3.3", Duration.ofMinutes(61));

        // 1.1.1.1 venceu (61 min) e saiu; 2.2.2.2 (31 min) e 3.3.3.3 ficam
        assertThat(filter.trackedKeys()).isEqualTo(2);
    }

    @Test
    void shouldNotSweepAgain_whenLastSweepIsWithinWindow() throws Exception {
        loginFrom("1.1.1.1", Duration.ZERO);
        loginFrom("2.2.2.2", Duration.ofMinutes(30));
        loginFrom("3.3.3.3", Duration.ofMinutes(61)); // varre aqui

        loginFrom("4.4.4.4", Duration.ofMinutes(100));

        // 2.2.2.2 já venceu (70 min), mas a última varredura foi há 39 min: continua no mapa
        assertThat(filter.trackedKeys()).isEqualTo(3);
    }

    @Test
    void shouldLimitDeleteAccount_whenSixthAttemptFromSameIp() throws Exception {
        when(clock.instant()).thenReturn(T0);
        int lastStatus = 0;
        for (int i = 0; i < 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/auth/account");
            request.setRemoteAddr("6.6.6.6");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            lastStatus = response.getStatus();
        }

        assertThat(lastStatus).isEqualTo(429);
    }

    @Test
    void shouldNotLimit_whenSameRouteWithOtherMethod() throws Exception {
        when(clock.instant()).thenReturn(T0);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/account");
        request.setRemoteAddr("7.7.7.7");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(filter.trackedKeys()).isZero();
    }

    @Test
    void shouldStillLimit_afterEvictionRuns() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(loginFrom("5.5.5.5", Duration.ofMinutes(61))).isEqualTo(200);
        }

        assertThat(loginFrom("5.5.5.5", Duration.ofMinutes(61))).isEqualTo(429);
    }
}
