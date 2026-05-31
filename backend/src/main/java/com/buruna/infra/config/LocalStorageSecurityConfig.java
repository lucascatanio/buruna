package com.buruna.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
@Profile("local")
public class LocalStorageSecurityConfig {

    @Bean
    public WebSecurityCustomizer localStorageSecurity() {
        return web -> web.ignoring().requestMatchers("/local-storage/**");
    }
}
