package com.buruna.shared.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

public interface StorageClient {

    record FileMetadata(String md5, long size) {}

    String upload(InputStream content, String fileName, String contentType, long contentLength);

    void delete(String fileName);

    URL generateSignedUrl(String fileName, Duration expiration);

    URL generateUploadSignedUrl(String objectName, Duration expiration);

    FileMetadata getFileMetadata(String objectName);
}
