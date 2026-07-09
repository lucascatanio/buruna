package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class InvalidUsernameException extends DomainException {

    public InvalidUsernameException(String value) {
        super(DomainErrorType.VALIDATION,
                "Nome de usuário inválido: deve ter entre 3 e 50 caracteres (recebido: \"" + value + "\")");
    }
}
