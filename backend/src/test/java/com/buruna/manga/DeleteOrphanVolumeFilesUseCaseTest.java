package com.buruna.manga;

import com.buruna.manga.application.maintenance.DeleteOrphanVolumeFilesUseCase;
import com.buruna.manga.application.maintenance.OrphanVolumeFilesResult;
import com.buruna.manga.persistence.VolumeRepository;
import com.buruna.shared.storage.StorageClient;
import com.buruna.shared.storage.StorageClient.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteOrphanVolumeFilesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z");

    private StorageClient storageClient;
    private VolumeRepository volumeRepository;
    private DeleteOrphanVolumeFilesUseCase useCase;

    @BeforeEach
    void setUp() {
        storageClient = mock(StorageClient.class);
        volumeRepository = mock(VolumeRepository.class);
        useCase = new DeleteOrphanVolumeFilesUseCase(
                storageClient, volumeRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(volumeRepository.findExistingFileUrls(anyCollection())).thenReturn(List.of());
    }

    @Test
    void shouldDeleteObject_whenOrphanOlderThanGracePeriod() {
        when(storageClient.list("volumes/")).thenReturn(List.of(object("volumes/a/old.pdf", 8)));

        OrphanVolumeFilesResult result = useCase.run();

        verify(storageClient).delete("volumes/a/old.pdf");
        assertThat(result).isEqualTo(new OrphanVolumeFilesResult(1, 1, 1));
    }

    @Test
    void shouldKeepObject_whenOrphanYoungerThanGracePeriod() {
        when(storageClient.list("volumes/")).thenReturn(List.of(object("volumes/a/new.pdf", 6)));

        OrphanVolumeFilesResult result = useCase.run();

        verify(storageClient, never()).delete(anyString());
        assertThat(result).isEqualTo(new OrphanVolumeFilesResult(1, 0, 0));
    }

    @Test
    void shouldKeepObject_whenVolumeRowExists() {
        when(storageClient.list("volumes/")).thenReturn(List.of(object("volumes/a/used.pdf", 30)));
        when(volumeRepository.findExistingFileUrls(anyCollection())).thenReturn(List.of("volumes/a/used.pdf"));

        OrphanVolumeFilesResult result = useCase.run();

        verify(storageClient, never()).delete(anyString());
        assertThat(result).isEqualTo(new OrphanVolumeFilesResult(1, 0, 0));
    }

    @Test
    void shouldContinueWithNextObjects_whenDeleteFails() {
        when(storageClient.list("volumes/")).thenReturn(List.of(
                object("volumes/a/first.pdf", 10),
                object("volumes/a/second.pdf", 10)));
        doThrow(new RuntimeException("gcs down")).when(storageClient).delete("volumes/a/first.pdf");

        OrphanVolumeFilesResult result = useCase.run();

        verify(storageClient).delete("volumes/a/second.pdf");
        assertThat(result).isEqualTo(new OrphanVolumeFilesResult(2, 2, 1));
    }

    @Test
    void shouldQueryRepositoryInBatches_whenManyObjects() {
        List<StoredObject> many = java.util.stream.IntStream.range(0, 1200)
                .mapToObj(i -> object("volumes/a/" + i + ".pdf", 1)).toList();
        when(storageClient.list("volumes/")).thenReturn(many);

        useCase.run();

        verify(volumeRepository, org.mockito.Mockito.times(3)).findExistingFileUrls(anyCollection());
    }

    private static StoredObject object(String name, int daysOld) {
        return new StoredObject(name, NOW.minus(Duration.ofDays(daysOld)));
    }
}
