package com.buruna.shared.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public interface EmailSender {

    Logger LOG = LoggerFactory.getLogger(EmailSender.class);

    /** Propaga {@link EmailDeliveryException} em caso de falha — usado onde a falha de
     * entrega precisa ser tratada por quem chamou (ex.: reset de senha via Pub/Sub, para
     * a mensagem ser reentregue). */
    void sendOrFail(String to, String subject, String body);

    /** Best-effort: engole {@link EmailDeliveryException} e loga WARN (comportamento
     * anterior, mantido para os fluxos que não precisam de retry). */
    default void send(String to, String subject, String body) {
        try {
            sendOrFail(to, subject, body);
        } catch (EmailDeliveryException e) {
            LOG.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    /** Um e-mail individual por destinatário — nenhum vê o endereço dos outros. */
    default void sendToEach(List<String> recipients, String subject, String body) {
        sendBatch(recipients.stream().map(to -> new OutgoingEmail(to, subject, body)).toList());
    }

    /** Best-effort, como {@link #send}: cada e-mail do lote com conteúdo próprio. */
    default void sendBatch(List<OutgoingEmail> emails) {
        emails.forEach(email -> send(email.to(), email.subject(), email.body()));
    }
}
