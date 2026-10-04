package com.buruna.shared.exception;

/**
 * O objeto pedido não existe no storage. Separada de {@link StorageException} para que o
 * chamador distinga "não existe" (ex.: finalize repetido, objeto já movido) de falha de
 * infraestrutura.
 */
public class StorageObjectNotFoundException extends StorageException {

    public StorageObjectNotFoundException(String message) {
        super(message);
    }

    public StorageObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
