package com.buruna.shared.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface StorageClient {

    record FileMetadata(String md5, long size) {}

    /**
     * URL assinada de upload e os headers que o cliente PUT precisa enviar para a
     * assinatura bater — ex.: {@code x-goog-content-length-range} no GCS,
     * limitando o tamanho aceito pelo PUT sem o backend tocar o arquivo.
     */
    record StoredObject(String name, Instant createdAt) {}

    record SignedUpload(URL url, Map<String, String> requiredHeaders) {}

    String upload(InputStream content, String fileName, String contentType, long contentLength);

    void delete(String fileName);

    URL generateSignedUrl(String fileName, Duration expiration);

    SignedUpload generateUploadSignedUrl(String objectName, Duration expiration);

    void move(String from, String to);

    FileMetadata getFileMetadata(String objectName);

    /**
     * Abre o conteúdo do objeto para leitura. Quem chama fecha o stream. Objeto ausente →
     * {@code StorageObjectNotFoundException}.
     */
    InputStream openRead(String objectName);

    /** Lista todos os objetos cujo nome começa com {@code prefix}, com a data de criação de cada um. */
    List<StoredObject> list(String prefix);
}
