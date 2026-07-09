package com.buruna.manga.exception;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * O ator não é dono do mangá nem ADMIN. Exceção de domínio pura (ADR-33):
 * {@link DomainErrorType#FORBIDDEN} → HTTP 403. Substitui a checagem de posse inline
 * que vivia em MangaService/VolumeService (ADR-35).
 */
public final class MangaModificationDeniedException extends DomainException {

    public MangaModificationDeniedException() {
        super(DomainErrorType.FORBIDDEN, "Você não tem permissão para modificar este mangá");
    }
}
