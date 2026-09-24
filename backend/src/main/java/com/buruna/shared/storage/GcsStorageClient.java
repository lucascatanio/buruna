package com.buruna.shared.storage;

import com.buruna.shared.exception.StorageException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GcsStorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(GcsStorageClient.class);

    private final Storage storage;
    private final String bucketName;
    private final ServiceAccountCredentials serviceAccountCredentials;
    private final long maxUploadBytes;

    public GcsStorageClient(Storage storage,
                            String bucketName,
                            GoogleCredentials credentials,
                            long maxUploadBytes) {
        this.storage = storage;
        this.bucketName = bucketName;
        this.maxUploadBytes = maxUploadBytes;
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
    public SignedUpload generateUploadSignedUrl(String objectName, Duration expiration) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectName))
                .setContentType("application/pdf")
                .build();
        // x-goog-content-length-range faz parte da assinatura: o GCS recusa o PUT se o
        // Content-Length do corpo estiver fora de [0, maxUploadBytes] (FIND-007) — sem
        // isso, um upload sem finalize nunca é limitado em tamanho.
        Map<String, String> requiredHeaders =
                Map.of("x-goog-content-length-range", "0," + maxUploadBytes);
        URL url = storage.signUrl(
                blobInfo,
                expiration.toMinutes(),
                TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.signWith(serviceAccountCredentials),
                Storage.SignUrlOption.withContentType(),
                Storage.SignUrlOption.withExtHeaders(requiredHeaders)
        );
        return new SignedUpload(url, requiredHeaders);
    }

    @Override
    public void move(String from, String to) {
        BlobId source = BlobId.of(bucketName, from);
        BlobId target = BlobId.of(bucketName, to);
        Storage.CopyRequest copyRequest = Storage.CopyRequest.newBuilder()
                .setSource(source)
                .setTarget(BlobInfo.newBuilder(target).build())
                .build();
        try {
            storage.copy(copyRequest).getResult();
        } catch (Exception e) {
            throw new StorageException("Falha ao mover objeto no GCS de " + from + " para " + to, e);
        }
        delete(from);
    }

    @Override
    public FileMetadata getFileMetadata(String objectName) {
        Blob blob = storage.get(bucketName, objectName);
        if (blob == null) {
            throw new StorageException("Objeto não encontrado no GCS: " + objectName, null);
        }
        return new FileMetadata(blob.getMd5(), blob.getSize());
    }
}