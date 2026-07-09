package com.buruna.shared.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Expõe um {@link Clock} como bean para que a lógica sensível a tempo (expiração
 * de token/URL, política de inatividade) use {@code now(clock)} em vez de
 * {@code OffsetDateTime.now()}, viabilizando testes determinísticos com
 * {@code Clock.fixed(...)} (ADR-36).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
