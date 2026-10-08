package com.buruna.manga;

import com.buruna.identity.domain.Email;
import com.buruna.identity.domain.Quota;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.Username;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.manga.domain.FileHash;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Slug;
import com.buruna.manga.domain.VolumeNumber;
import com.buruna.manga.persistence.MangaRepository;
import com.buruna.manga.persistence.VolumeRepository;
import com.buruna.shared.storage.StorageClient;
import com.buruna.shared.storage.StorageClient.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gatilho do job de órfãos do storage: POST /admin/jobs/storage-orphans. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class StorageOrphansJobIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /** Precisa bater com {@code app.jobs.secret} em application-test.yml. */
    private static final String CORRECT_SECRET = "test-jobs-secret";

    @Autowired MockMvc mockMvc;
    @Autowired MangaRepository mangaRepository;
    @Autowired VolumeRepository volumeRepository;
    @Autowired UserRepository userRepository;
    @MockitoBean StorageClient storageClient;

    @BeforeEach
    void setUp() {
        volumeRepository.deleteAllInBatch();
        mangaRepository.deleteAll();
        userRepository.deleteAllInBatch();
    }

    @Test
    void shouldReturn401_whenSecretIsWrong() throws Exception {
        mockMvc.perform(post("/admin/jobs/storage-orphans").header("X-Job-Secret", "not-the-secret"))
                .andExpect(status().isUnauthorized());

        verify(storageClient, never()).list(anyString());
    }

    @Test
    void shouldDeleteOnlyOldOrphan_whenSecretIsCorrect() throws Exception {
        User owner = userRepository.save(User.register(
                Email.of("owner@orphans.test"), Username.of("orphansOwner"),
                "$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC", "test",
                Quota.of(BigDecimal.ONE)));
        Manga manga = Manga.createPrivate(Slug.of("orphans-" + UUID.randomUUID()), "Private", "synopsis", owner.getId());
        manga.addVolume(VolumeNumber.of(1), "volumes/m/kept.pdf", FileHash.of("hash-kept"), 10L, owner.getId());
        mangaRepository.save(manga);

        Instant old = Instant.now().minus(Duration.ofDays(30));
        Instant recent = Instant.now().minus(Duration.ofDays(1));
        when(storageClient.list("volumes/")).thenReturn(List.of(
                new StoredObject("volumes/m/kept.pdf", old),
                new StoredObject("volumes/m/orphan-old.pdf", old),
                new StoredObject("volumes/m/orphan-recent.pdf", recent)));

        mockMvc.perform(post("/admin/jobs/storage-orphans").header("X-Job-Secret", CORRECT_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyzed").value(3))
                .andExpect(jsonPath("$.orphans").value(1))
                .andExpect(jsonPath("$.deleted").value(1));

        verify(storageClient).delete("volumes/m/orphan-old.pdf");
        verify(storageClient, never()).delete("volumes/m/kept.pdf");
        verify(storageClient, never()).delete("volumes/m/orphan-recent.pdf");
    }

    @Test
    void shouldReportWithoutDeleting_whenDryRun() throws Exception {
        when(storageClient.list("volumes/")).thenReturn(List.of(
                new StoredObject("volumes/m/orphan-old.pdf", Instant.now().minus(Duration.ofDays(30)))));

        mockMvc.perform(post("/admin/jobs/storage-orphans").param("dryRun", "true")
                        .header("X-Job-Secret", CORRECT_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orphans").value(1))
                .andExpect(jsonPath("$.deleted").value(0))
                .andExpect(jsonPath("$.dryRun").value(true));

        verify(storageClient, never()).delete(anyString());
    }
}
