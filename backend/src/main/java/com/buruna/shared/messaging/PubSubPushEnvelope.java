package com.buruna.shared.messaging;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Corpo que uma push subscription do Pub/Sub envia ao endpoint. */
public record PubSubPushEnvelope(Message message, String subscription) {

    public record Message(String data, String messageId) {
    }

    public String decodedData() {
        return new String(Base64.getDecoder().decode(message.data()), StandardCharsets.UTF_8);
    }
}
