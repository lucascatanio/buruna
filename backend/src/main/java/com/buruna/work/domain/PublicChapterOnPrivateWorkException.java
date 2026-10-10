package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class PublicChapterOnPrivateWorkException extends DomainException {

    public PublicChapterOnPrivateWorkException() {
        super(DomainErrorType.FORBIDDEN, "Use /my/works para gerenciar capítulos de obras privadas");
    }
}
