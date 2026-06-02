package com.buruna.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exceção de domínio legada que carrega o status HTTP — acopla regra de negócio
 * à camada web (ADR-33). Mantida temporariamente para coexistir com a nova base
 * pura {@link DomainException} durante a migração; cada contexto substitui seus
 * usos por exceções de domínio puras e, ao final, esta classe é removida.
 */
@Deprecated
@Getter
public class LegacyHttpDomainException extends RuntimeException {

    private final HttpStatus status;

    public LegacyHttpDomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

}
