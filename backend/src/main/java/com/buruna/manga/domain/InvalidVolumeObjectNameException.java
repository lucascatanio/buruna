package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * O {@code objectName} de upload não é um caminho pendente válido para o mangá do
 * request (ADR-40). Cobre formato malformado, UUID inválido em qualquer segmento e
 * {@code mangaId} do caminho divergente do mangá do request — o vetor do FIND-002.
 */
public final class InvalidVolumeObjectNameException extends DomainException {

    public InvalidVolumeObjectNameException(String objectName) {
        super(DomainErrorType.VALIDATION, "objectName de upload inválido: " + objectName);
    }
}
