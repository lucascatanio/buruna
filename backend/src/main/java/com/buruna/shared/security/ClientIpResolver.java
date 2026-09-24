package com.buruna.shared.security;

import com.buruna.shared.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolve o IP real do cliente a partir do {@code X-Forwarded-For}, contando um
 * número configurável de saltos de proxy confiável (infra própria) a partir da
 * DIREITA da lista.
 *
 * <p>O cliente controla o conteúdo do header, então a primeira entrada (a mais à
 * esquerda) não é confiável — ele pode forjar qualquer valor ali para escapar de
 * rate limit por IP. Cada proxy confiável no caminho (nginx do frontend, depois o
 * Cloud Run do backend) ACRESCENTA uma entrada à direita; {@code trustedProxyHops}
 * é quantos desses saltos existem, e a entrada logo à esquerda do bloco confiável
 * (posição {@code size - hops}) é o IP como visto pelo primeiro salto confiável —
 * o mais próximo do valor real do cliente que dá para confiar.
 *
 * <p>Limitação conhecida: quem chama o backend diretamente (sem passar pelo
 * frontend) ainda escolhe o valor que cai nessa posição.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final int trustedProxyHops;

    public ClientIpResolver(AppProperties appProperties) {
        this.trustedProxyHops = appProperties.security().trustedProxyHops();
    }

    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        // ligado só durante a calibração de APP_TRUSTED_PROXY_HOPS (docs/DEPLOYMENT.md)
        log.debug("X-Forwarded-For={} remoteAddr={}", forwarded, request.getRemoteAddr());
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }

        List<String> entries = new ArrayList<>();
        for (String candidate : forwarded.split(",")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }

        int index = entries.size() - trustedProxyHops;
        if (index < 0 || index >= entries.size()) {
            return request.getRemoteAddr();
        }
        return entries.get(index);
    }
}
