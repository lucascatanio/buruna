package com.buruna.shared.exception;

/**
 * Imagem em base64 (avatar, capa) com content-type fora do permitido, tamanho
 * decodificado acima do limite, ou base64 malformado. Exceção de domínio pura
 * (ADR-33) — vive em {@code shared} porque {@link com.buruna.shared.storage.StorageUploadHelper}
 * é compartilhado por identity (avatar) e manga (capa pública/privada).
 */
public final class InvalidImageUploadException extends DomainException {

    public InvalidImageUploadException(String message) {
        super(DomainErrorType.VALIDATION, message);
    }
}
