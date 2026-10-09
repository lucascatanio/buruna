package com.buruna.shared.storage;

import com.buruna.shared.exception.StorageObjectNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageClientTest {

    @TempDir
    Path storagePath;

    @Test
    void getFileMetadata_missingObject_throwsNotFound() {
        LocalStorageClient client = new LocalStorageClient(storagePath, "http://localhost");

        assertThatThrownBy(() -> client.getFileMetadata("pending/volumes/x/missing.pdf"))
                .isInstanceOf(StorageObjectNotFoundException.class);
    }

    @Test
    void move_missingSource_throwsNotFound() {
        LocalStorageClient client = new LocalStorageClient(storagePath, "http://localhost");

        assertThatThrownBy(() -> client.move("pending/volumes/x/missing.pdf", "volumes/x/missing.pdf"))
                .isInstanceOf(StorageObjectNotFoundException.class);
    }

    @Test
    void shouldListOnlyObjectsUnderPrefix_whenStorageHasOtherFolders() throws Exception {
        LocalStorageClient client = new LocalStorageClient(storagePath, "http://localhost");
        client.upload(new java.io.ByteArrayInputStream(new byte[]{1}), "volumes/m/a.pdf", "application/pdf", 1);
        client.upload(new java.io.ByteArrayInputStream(new byte[]{1}), "pending/volumes/m/b.pdf", "application/pdf", 1);

        var objects = client.list("volumes/");

        org.assertj.core.api.Assertions.assertThat(objects)
                .extracting(StorageClient.StoredObject::name)
                .containsExactly("volumes/m/a.pdf");
    }
}
