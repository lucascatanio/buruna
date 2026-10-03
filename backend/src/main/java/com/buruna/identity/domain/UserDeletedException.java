package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class UserDeletedException extends DomainException {

    public UserDeletedException() {
        super(DomainErrorType.CONFLICT, "Conta removida não pode mudar de status");
    }
}
