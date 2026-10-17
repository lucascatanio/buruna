package com.buruna.shared.source;

public class ExternalWorkNotFoundException extends ExternalSourceException {

    public ExternalWorkNotFoundException(String externalId) {
        super("Obra não encontrada na fonte: " + externalId);
    }
}
