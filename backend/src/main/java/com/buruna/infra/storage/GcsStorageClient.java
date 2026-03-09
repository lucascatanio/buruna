package com.buruna.infra.storage;

import com.buruna.infra.exception.StorageException;
import com.google.cloud.storage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class GcsStorageClient implements StorageClient {

    private final Storage storage;
    private final String bucketName;

    public GcsStorageClient(Storage storage,
                            @Value("${app.gcs.bucket-name}") String bucketName) {
        this.storage = storage;
        this.bucketName = bucketName;
    }

    @Override
    public String upload(InputStream content, String fileName, String contentType, long contentLength) {
        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();
        try {
            storage.createFrom(blobInfo, content);
        } catch (IOException e) {
            throw new StorageException("Falha ao fazer upload do arquivo para o GCS: " + fileName, e);
        }
        return fileName;
    }

    @Override
    public void delete(String fileName) {
        boolean deleted = storage.delete(BlobId.of(bucketName, fileName));
        if (!deleted) {
            throw new StorageException("Arquivo não encontrado no GCS para deleção: " + fileName);
        }
    }

    @Override
    public URL generateSignedUrl(String fileName, Duration expiration) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, fileName)).build();
        return storage.signUrl(
                blobInfo,
                expiration.toMinutes(),
                TimeUnit.MINUTES,
                Storage.SignUrlOption.withV4Signature()
        );
    }
}
