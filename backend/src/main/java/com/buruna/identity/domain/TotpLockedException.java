package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class TotpLockedException extends DomainException {

    public TotpLockedException() {
        super(DomainErrorType.RATE_LIMITED, "Too many invalid TOTP attempts. Try again later.");
    }
}
