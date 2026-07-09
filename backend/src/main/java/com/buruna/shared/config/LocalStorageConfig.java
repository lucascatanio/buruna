package com.buruna.shared.config;

import com.buruna.shared.storage.LocalStorageClient;
import com.buruna.shared.storage.StorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;

@Configuration
@Profile("local")
public class LocalStorageConfig {

    @Bean
    public StorageClient localStorageClient(
            @Value("${app.storage.local.path}") Path storagePath,
            @Value("${app.storage.local.base-url:http://localhost}") String baseUrl) {
        return new LocalStorageClient(storagePath, baseUrl);
    }
}
