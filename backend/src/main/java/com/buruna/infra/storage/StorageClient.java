package com.buruna.infra.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

public interface StorageClient {
    String upload(InputStream content, String fileName, String contentType, long contentLength);

    void delete(String fileName);

    URL generateSignedUrl(String fileName, Duration expiration);

    URL generateUploadSignedUrl(String objectName, Duration expiration);

    com.google.cloud.storage.Blob getBlob(String objectName);
}
