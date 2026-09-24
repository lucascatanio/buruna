package com.buruna.shared.messaging;

import com.buruna.shared.exception.MessagingException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Publica mensagens no Pub/Sub pela API REST. A publicação acontece dentro da requisição
 * e espera a confirmação: o SDK oficial publica em lote numa thread de fundo, que o Cloud
 * Run (cpu-throttling) congela depois da resposta HTTP (ADR-42).
 */
public class PubSubPublisher {

    private static final String PUBLISH_URL = "https://pubsub.googleapis.com/v1/%s:publish";

    private final RestTemplate restTemplate;
    private final Supplier<String> accessToken;

    public PubSubPublisher(RestTemplate restTemplate, Supplier<String> accessToken) {
        this.restTemplate = restTemplate;
        this.accessToken = accessToken;
    }

    /** {@code topic} no formato {@code projects/<projeto>/topics/<nome>}. */
    public void publish(String topic, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken.get());
        String data = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> body = Map.of("messages", List.of(Map.of("data", data)));
        try {
            restTemplate.exchange(PUBLISH_URL.formatted(topic), HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class);
        } catch (RestClientException e) {
            throw new MessagingException("Falha ao publicar no Pub/Sub: " + topic, e);
        }
    }
}
