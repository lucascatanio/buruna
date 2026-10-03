package com.buruna.identity.application.account;

import com.buruna.shared.messaging.PubSubPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Publica o pedido de reset de senha no Pub/Sub em vez de processá-lo na própria
 * requisição: o endpoint responde em tempo constante independente de o e-mail existir,
 * o que impede enumerar contas pelo timing do envio ao Resend (ADR-42).
 */
@Component
@Profile("!local")
public class PubSubPasswordResetRequests implements PasswordResetRequests {

    private final PubSubPublisher publisher;
    private final ObjectMapper objectMapper;
    private final String topic;

    public PubSubPasswordResetRequests(PubSubPublisher publisher, ObjectMapper objectMapper,
                                       @Value("${app.pubsub.password-reset-topic:}") String topic) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.topic = topic;
        if (topic.isBlank()) {
            throw new IllegalStateException(
                    "app.pubsub.password-reset-topic (APP_PUBSUB_PASSWORD_RESET_TOPIC) é obrigatório fora do profile 'local'");
        }
    }

    @Override
    public void submit(String email) {
        try {
            publisher.publish(topic, objectMapper.writeValueAsString(new PasswordResetMessage(email)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar PasswordResetMessage", e);
        }
    }
}
