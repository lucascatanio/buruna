package com.buruna.sourcing.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/**
 * O manga-downloader não conseguiu listar a série (site fora da lista, fora do ar, ou que exige
 * navegador, que o serviço web não tem). Leva o motivo que a CLI deu, para o ADM decidir.
 */
public final class SiteListingFailedException extends DomainException {

    public SiteListingFailedException(String reason) {
        super(DomainErrorType.UNPROCESSABLE, "Não deu para listar esta série. " + reason);
    }
}
