package com.buruna.shared.config;

import com.buruna.shared.jobs.CloudRunJobLauncher;
import com.buruna.shared.jobs.JobLaunchException;
import com.buruna.shared.jobs.JobLauncher;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.time.Duration;

@Configuration
@Profile("!local")
public class IngestJobConfig {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // Credencial da conta de serviço do Cloud Run (ADC), como no PubSubConfig.
    @Bean
    public JobLauncher jobLauncher(@Value("${app.ingest.job-name}") String jobName) throws IOException {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalStateException("app.ingest.job-name (APP_INGEST_JOB_NAME) é obrigatório fora do profile local");
        }
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE);
        return new CloudRunJobLauncher(
                new RestTemplateBuilder().connectTimeout(TIMEOUT).readTimeout(TIMEOUT).build(),
                () -> {
                    try {
                        credentials.refreshIfExpired();
                        return credentials.getAccessToken().getTokenValue();
                    } catch (IOException e) {
                        throw new JobLaunchException("Falha ao obter token para o Cloud Run", e);
                    }
                },
                jobName);
    }
}
