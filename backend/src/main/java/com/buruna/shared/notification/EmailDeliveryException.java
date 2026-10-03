package com.buruna.shared.notification;

/** Falha ao entregar um e-mail. Propagada por {@link EmailSender#sendOrFail} para quem precisa
 * saber que o envio não aconteceu (ex.: reset de senha via Pub/Sub, onde a falha deve
 * disparar uma nova entrega da mensagem). */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
