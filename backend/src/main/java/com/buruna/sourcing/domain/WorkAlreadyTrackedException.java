package com.buruna.sourcing.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/** A obra da fonte, ou a obra do Burūna, já está sendo acompanhada. */
public final class WorkAlreadyTrackedException extends DomainException {

    public WorkAlreadyTrackedException(String message) {
        super(DomainErrorType.CONFLICT, message);
    }
}
