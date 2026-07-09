package com.buruna.shared.config;

import com.buruna.shared.storage.GcsStorageClient;
import com.buruna.shared.storage.StorageClient;
import com.google.api.services.storage.StorageScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@Profile("!local")
public class GcsConfig {

    @Value("${app.gcs.credentials-path:}")
    private String credentialsPath;

    @Bean
    @Qualifier("gcsCredentials")
    public GoogleCredentials gcsCredentials() throws IOException {
        GoogleCredentials credentials;
        if (credentialsPath != null && !credentialsPath.isBlank() && new File(credentialsPath).exists()) {
            credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }
        return credentials.createScoped(StorageScopes.DEVSTORAGE_FULL_CONTROL);
    }

    @Bean
    public Storage googleCloudStorage(@Qualifier("gcsCredentials") GoogleCredentials credentials) {
        return StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();
    }

    @Bean
    public StorageClient gcsStorageClient(Storage storage,
                                          @Qualifier("gcsCredentials") GoogleCredentials credentials,
                                          @Value("${app.gcs.bucket-name}") String bucketName) {
        return new GcsStorageClient(storage, bucketName, credentials);
    }
}
