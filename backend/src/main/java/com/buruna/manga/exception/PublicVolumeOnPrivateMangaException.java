package com.buruna.manga.exception;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * Tentativa de gerenciar volumes de um mangá privado pelos endpoints públicos
 * ({@code /mangas/...}). Exceção de domínio pura (ADR-33): {@link DomainErrorType#FORBIDDEN}
 * → HTTP 403. O fluxo correto é {@code /my/mangas}.
 */
public final class PublicVolumeOnPrivateMangaException extends DomainException {

    public PublicVolumeOnPrivateMangaException() {
        super(DomainErrorType.FORBIDDEN, "Use /my/mangas para gerenciar volumes de mangás privados");
    }
}
