package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * Tentativa de gerenciar volumes de um mangá privado pelos endpoints públicos
 * ({@code /works/...}). Exceção de domínio pura (ADR-33): {@link DomainErrorType#FORBIDDEN}
 * → HTTP 403. O fluxo correto é {@code /my/works}.
 */
public final class PublicVolumeOnPrivateWorkException extends DomainException {

    public PublicVolumeOnPrivateWorkException() {
        super(DomainErrorType.FORBIDDEN, "Use /my/works para gerenciar volumes de mangás privados");
    }
}
