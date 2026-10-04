package com.buruna.identity.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * Senha ou código 2FA errados ao confirmar uma ação sobre a própria conta. É FORBIDDEN, não
 * 401: o usuário está autenticado, e um 401 numa requisição autenticada faria o frontend
 * tentar refresh e reenviar, contando a falha de 2FA duas vezes.
 */
public final class AccountOwnershipNotConfirmedException extends DomainException {

    public AccountOwnershipNotConfirmedException() {
        super(DomainErrorType.FORBIDDEN, "Senha ou código 2FA incorretos");
    }
}
