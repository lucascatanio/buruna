package com.buruna.shared.media;

/** Arquivo enviado inválido. A mensagem é exibida ao usuário final, então é curta e em português. */
public class InvalidArchiveException extends RuntimeException {

    public InvalidArchiveException(String message) {
        super(message);
    }

    public InvalidArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
