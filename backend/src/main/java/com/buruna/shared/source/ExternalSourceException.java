package com.buruna.shared.source;

/** Falha ao falar com a fonte: rede, limite de taxa estourado ou resposta fora do esperado. */
public class ExternalSourceException extends RuntimeException {

    public ExternalSourceException(String message) {
        super(message);
    }

    public ExternalSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
