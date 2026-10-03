package com.buruna.shared.config;

import com.buruna.shared.exception.MessagingException;
import com.buruna.shared.messaging.PubSubPublisher;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.time.Duration;

@Configuration
@Profile("!local")
public class PubSubConfig {

    private static final String PUBSUB_SCOPE = "https://www.googleapis.com/auth/pubsub";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // Credencial da conta de serviço do Cloud Run (ADC), não a chave usada para assinar URLs do GCS.
    @Bean
    public PubSubPublisher pubSubPublisher() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault().createScoped(PUBSUB_SCOPE);
        return new PubSubPublisher(
                new RestTemplateBuilder().connectTimeout(TIMEOUT).readTimeout(TIMEOUT).build(),
                () -> {
                    try {
                        credentials.refreshIfExpired();
                        return credentials.getAccessToken().getTokenValue();
                    } catch (IOException e) {
                        throw new MessagingException("Falha ao obter token para o Pub/Sub", e);
                    }
                });
    }
}
