package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/** Vínculo da obra com o AniList fora da regra: id inválido, ou outro id numa obra já vinculada. */
public final class InvalidAniListLinkException extends DomainException {

    public InvalidAniListLinkException(String message) {
        super(DomainErrorType.CONFLICT, message);
    }
}
