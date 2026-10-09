package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * O ator autenticado não é dono do mangá privado. Exceção de domínio pura (ADR-33):
 * {@link DomainErrorType#FORBIDDEN} → HTTP 403. Substitui a checagem de posse inline
 * que vivia em PrivateWorkService (ADR-35).
 */
public final class PrivateWorkAccessDeniedException extends DomainException {

    public PrivateWorkAccessDeniedException() {
        super(DomainErrorType.FORBIDDEN, "Você não tem permissão para modificar este mangá");
    }
}
