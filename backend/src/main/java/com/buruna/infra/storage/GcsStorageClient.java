package com.buruna.infra.storage;

import com.buruna.infra.exception.StorageException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class GcsStorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(GcsStorageClient.class);

    private final Storage storage;
    private final String bucketName;
    private final ServiceAccountCredentials serviceAccountCredentials;

    public GcsStorageClient(Storage storage,
                            @Value("${app.gcs.bucket-name}") String bucketName,
                            @Qualifier("gcsCredentials") GoogleCredentials credentials) {
        this.storage = storage;
        this.bucketName = bucketName;
        if (credentials instanceof ServiceAccountCredentials saCreds) {
            this.serviceAccountCredentials = saCreds;
        } else {
            throw new IllegalStateException(
                    "GCS credentials must be ServiceAccountCredentials for URL signing");
        }
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
        try {
            boolean deleted = storage.delete(bucketName, fileName);
            if (!deleted) {
                log.warn("GCS object not found for deletion: {}", fileName);
            }
        } catch (Exception e) {
            log.warn("Failed to delete GCS object {}: {}", fileName, e.getMessage());
        }
    }

    @Override
    public URL generateSignedUrl(String fileName, Duration expiration) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, fileName)).build();
        return storage.signUrl(
                blobInfo,
                expiration.toMinutes(),
                TimeUnit.MINUTES,
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.signWith(serviceAccountCredentials)
        );
    }

    @Override
    public URL generateUploadSignedUrl(String objectName, Duration expiration) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectName))
                .setContentType("application/pdf")
                .build();
        return storage.signUrl(
                blobInfo,
                expiration.toMinutes(),
                TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.signWith(serviceAccountCredentials),
                Storage.SignUrlOption.withContentType()
        );
    }

    @Override
    public Blob getBlob(String objectName) {
        Blob blob = storage.get(bucketName, objectName);
        if (blob == null) {
            throw new StorageException("Objeto não encontrado no GCS: " + objectName, null);
        }
        return blob;
    }
}