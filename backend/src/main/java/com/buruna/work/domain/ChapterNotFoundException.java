package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class ChapterNotFoundException extends DomainException {

    public ChapterNotFoundException(UUID chapterId) {
        super(DomainErrorType.NOT_FOUND, "Capítulo não encontrado: " + chapterId);
    }
}
