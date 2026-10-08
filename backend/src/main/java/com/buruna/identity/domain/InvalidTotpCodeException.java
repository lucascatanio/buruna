package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * Código 2FA errado ou reutilizado ao ativar ou desativar a 2FA. É FORBIDDEN, não 401: o
 * usuário está autenticado, e um 401 numa requisição autenticada faria o frontend tentar
 * refresh e reenviar, contando a falha de 2FA duas vezes.
 */
public final class InvalidTotpCodeException extends DomainException {

    public InvalidTotpCodeException() {
        super(DomainErrorType.FORBIDDEN, "Código 2FA incorreto");
    }
}
