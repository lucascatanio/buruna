package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class ChapterAccessDeniedException extends DomainException {

    public ChapterAccessDeniedException(UUID chapterId) {
        super(DomainErrorType.FORBIDDEN, "Sem acesso ao capítulo " + chapterId);
    }
}
