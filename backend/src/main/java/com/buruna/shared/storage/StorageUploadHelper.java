package com.buruna.shared.storage;

import com.buruna.shared.exception.InvalidImageUploadException;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public final class StorageUploadHelper {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    // extensão SEMPRE derivada deste mapa fixo, nunca do input do cliente — content-type
    // fora dele (ex.: text/html) é rejeitado antes de chegar ao storage.
    private static final Map<String, String> ALLOWED_EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp"
    );

    private StorageUploadHelper() {}

    // faz upload de imagem em base64 (data URI ou base64 puro) para o GCS
    public static String uploadBase64Image(
            StorageClient storageClient,
            String base64Input,
            String folder
    ) {
        String base64Data;
        String contentType;

        if (base64Input.startsWith("data:")) {
            int commaIndex = base64Input.indexOf(',');
            if (commaIndex < 0) {
                throw new InvalidImageUploadException("Data URI malformada");
            }
            String header = base64Input.substring(5, commaIndex);
            contentType = header.split(";")[0];
            base64Data = base64Input.substring(commaIndex + 1);
        } else {
            contentType = "image/jpeg";
            base64Data = base64Input;
        }

        String extension = ALLOWED_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new InvalidImageUploadException(
                    "Tipo de imagem não permitido: " + contentType);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw new InvalidImageUploadException("Conteúdo base64 inválido");
        }

        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new InvalidImageUploadException(
                    "Imagem excede o tamanho máximo permitido (5 MB)");
        }

        String objectName = folder + "/" + UUID.randomUUID() + "." + extension;

        storageClient.upload(
                new ByteArrayInputStream(bytes),
                objectName,
                contentType,
                bytes.length
        );

        return objectName;
    }
}
