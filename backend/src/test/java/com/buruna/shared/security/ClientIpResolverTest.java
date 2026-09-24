package com.buruna.shared.security;

import com.buruna.shared.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste unitário puro (sem contexto Spring): prova que o IP lido é sempre a
 * entrada acrescentada pelo salto de proxy confiável — nunca uma entrada que o
 * cliente forjou no início do header (FIND-003).
 */
class ClientIpResolverTest {

    private static ClientIpResolver resolverWithHops(int hops) {
        AppProperties appProperties = new AppProperties(
                null, null, null, null, null,
                new AppProperties.SecurityProperties(hops), null);
        return new ClientIpResolver(appProperties);
    }

    private static HttpServletRequest requestWith(String xForwardedFor, String remoteAddr) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        return request;
    }

    @Test
    void shouldIgnoreForgedEntries_andReturnLastEntry_whenOneHopTrusted() {
        // O cliente forjou duas entradas na frente; só a última (acrescentada pelo
        // nosso proxy) é confiável com 1 salto configurado.
        HttpServletRequest request = requestWith("1.1.1.1, 2.2.2.2, 9.9.9.9", "127.0.0.1");

        String ip = resolverWithHops(1).resolve(request);

        assertThat(ip).isEqualTo("9.9.9.9");
    }

    @Test
    void shouldReturnEntryBeforeTrustedBlock_whenTwoHopsTrusted() {
        // Com 2 saltos confiáveis, a entrada válida é a penúltima (size - 2).
        HttpServletRequest request = requestWith("1.1.1.1, 2.2.2.2, 9.9.9.9", "127.0.0.1");

        String ip = resolverWithHops(2).resolve(request);

        assertThat(ip).isEqualTo("2.2.2.2");
    }

    @Test
    void shouldFallBackToRemoteAddr_whenXForwardedForIsAbsent() {
        HttpServletRequest request = requestWith(null, "203.0.113.9");

        String ip = resolverWithHops(1).resolve(request);

        assertThat(ip).isEqualTo("203.0.113.9");
    }

    @Test
    void shouldFallBackToRemoteAddr_whenXForwardedForIsBlank() {
        HttpServletRequest request = requestWith("   ", "203.0.113.9");

        String ip = resolverWithHops(1).resolve(request);

        assertThat(ip).isEqualTo("203.0.113.9");
    }

    @Test
    void shouldFallBackToRemoteAddr_whenListIsShorterThanTrustedHops() {
        // Só 1 entrada no header, mas a config espera 2 saltos confiáveis: não dá
        // para confiar em nenhuma posição, então cai no IP de conexão TCP.
        HttpServletRequest request = requestWith("9.9.9.9", "203.0.113.9");

        String ip = resolverWithHops(2).resolve(request);

        assertThat(ip).isEqualTo("203.0.113.9");
    }

    @Test
    void shouldTrimWhitespace_aroundEachEntry() {
        HttpServletRequest request = requestWith("  1.1.1.1  ,  9.9.9.9  ", "127.0.0.1");

        String ip = resolverWithHops(1).resolve(request);

        assertThat(ip).isEqualTo("9.9.9.9");
    }
}
