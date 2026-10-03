package com.buruna.identity.web;

import com.buruna.identity.application.account.PasswordResetMessage;
import com.buruna.identity.application.account.ProcessPasswordResetRequestUseCase;
import com.buruna.shared.messaging.PubSubPushEnvelope;
import com.buruna.shared.security.PubSubPushAuthenticator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interno que a push subscription do Pub/Sub chama para processar o pedido de
 * reset de senha publicado por {@code PubSubPasswordResetRequests}. Público no Spring
 * Security ({@code /internal/pubsub/**}); a autenticação é o token OIDC verificado por
 * {@link PubSubPushAuthenticator} (ADR-42).
 */
@RestController
@RequestMapping("/internal/pubsub")
public class PasswordResetPushController {

    private final PubSubPushAuthenticator pubSubPushAuthenticator;
    private final ProcessPasswordResetRequestUseCase processPasswordResetRequest;
    private final ObjectMapper objectMapper;

    public PasswordResetPushController(PubSubPushAuthenticator pubSubPushAuthenticator,
                                       ProcessPasswordResetRequestUseCase processPasswordResetRequest,
                                       ObjectMapper objectMapper) {
        this.pubSubPushAuthenticator = pubSubPushAuthenticator;
        this.processPasswordResetRequest = processPasswordResetRequest;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody PubSubPushEnvelope envelope) throws JsonProcessingException {
        pubSubPushAuthenticator.verify(authorizationHeader);

        PasswordResetMessage message =
                objectMapper.readValue(envelope.decodedData(), PasswordResetMessage.class);
        processPasswordResetRequest.handle(message.email());

        return ResponseEntity.noContent().build();
    }
}
