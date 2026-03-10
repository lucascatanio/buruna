package com.buruna.infra.storage;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.UUID;

public final class StorageUploadHelper {

    private StorageUploadHelper() {}

    // faz upload de imagem em base64 (data URI ou base64 puro) para o GCS
    public static String uploadBase64Image(
            StorageClient storageClient,
            String base64Input,
            String folder
    ) {
        String base64Data;
        String contentType = "image/jpeg";

        if (base64Input.startsWith("data:")) {
            int commaIndex = base64Input.indexOf(',');
            String header = base64Input.substring(5, commaIndex);
            contentType = header.split(";")[0];
            base64Data = base64Input.substring(commaIndex + 1);
        } else {
            base64Data = base64Input;
        }

        String extension = contentType.contains("/") ? contentType.split("/")[1] : "jpg";
        String objectName = folder + "/" + UUID.randomUUID() + "." + extension;
        byte[] bytes = Base64.getDecoder().decode(base64Data);

        storageClient.upload(
                new ByteArrayInputStream(bytes),
                objectName,
                contentType,
                bytes.length
        );

        return objectName;
    }
}