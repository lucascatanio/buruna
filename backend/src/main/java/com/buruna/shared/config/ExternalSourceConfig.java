package com.buruna.shared.config;

import com.buruna.shared.source.ExternalSource;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.mangadex.MangaDexSource;
import com.buruna.shared.time.Sleeper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class ExternalSourceConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REPORT_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    public ExternalSource externalSource(@Value("${app.sourcing.user-agent}") String userAgent, Clock clock,
                                         Sleeper sleeper) {
        return new MangaDexSource(restTemplate(CONNECT_TIMEOUT, READ_TIMEOUT), userAgent, clock, sleeper,
                restTemplate(REPORT_TIMEOUT, REPORT_TIMEOUT));
    }

    // sem seguir redirect: a URL é conferida antes da chamada (MangaDexSource.requireSafeImageUrl),
    // e um redirect levaria a requisição para um endereço que ninguém conferiu
    private static RestTemplate restTemplate(Duration connectTimeout, Duration readTimeout) {
        CloseableHttpClient client = HttpClients.custom().disableRedirectHandling().build();
        return new RestTemplateBuilder()
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(client))
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
    }

    @Bean
    public Sleeper sleeper() {
        return ExternalSourceConfig::sleep;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalSourceException("Espera interrompida", e);
        }
    }
}
