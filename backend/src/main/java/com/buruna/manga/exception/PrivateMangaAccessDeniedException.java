package com.buruna.manga.exception;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * O ator autenticado não é dono do mangá privado. Exceção de domínio pura (ADR-33):
 * {@link DomainErrorType#FORBIDDEN} → HTTP 403. Substitui a checagem de posse inline
 * (LegacyHttpDomainException) que vivia em PrivateMangaService (ADR-35).
 */
public final class PrivateMangaAccessDeniedException extends DomainException {

    public PrivateMangaAccessDeniedException() {
        super(DomainErrorType.FORBIDDEN, "Você não tem permissão para modificar este mangá");
    }
}
