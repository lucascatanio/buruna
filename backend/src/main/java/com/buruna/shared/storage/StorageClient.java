package com.buruna.shared.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Map;

public interface StorageClient {

    record FileMetadata(String md5, long size) {}

    /**
     * URL assinada de upload e os headers que o cliente PUT precisa enviar para a
     * assinatura bater — ex.: {@code x-goog-content-length-range} no GCS,
     * limitando o tamanho aceito pelo PUT sem o backend tocar o arquivo.
     */
    record SignedUpload(URL url, Map<String, String> requiredHeaders) {}

    String upload(InputStream content, String fileName, String contentType, long contentLength);

    void delete(String fileName);

    URL generateSignedUrl(String fileName, Duration expiration);

    SignedUpload generateUploadSignedUrl(String objectName, Duration expiration);

    void move(String from, String to);

    FileMetadata getFileMetadata(String objectName);
}
