package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class DuplicateChapterException extends DomainException {

    public DuplicateChapterException(ChapterNumber number, Language language) {
        super(DomainErrorType.CONFLICT,
                "Já existe o capítulo " + number + " em " + language + " para esta obra");
    }
}
