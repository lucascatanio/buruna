package com.buruna.manga;

import com.buruna.manga.application.maintenance.DeletePrivateCollectionForUserUseCase;
import com.buruna.manga.domain.FileHash;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Slug;
import com.buruna.manga.domain.VolumeNumber;
import com.buruna.manga.persistence.MangaRepository;
import com.buruna.manga.persistence.VolumeRepository;
import com.buruna.identity.domain.Email;
import com.buruna.identity.domain.Quota;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.domain.Username;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.shared.storage.StorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Integração do use case público [4.8] que o job de inatividade (Epic 5.3) vai consumir.
 *
 * Verifica a fronteira do roadmap §7 [4.8]: a deleção do banco acontece dentro da tx, os
 * object names do GCS (capa + volumes dos mangás privados) são apenas COLETADOS e retornados,
 * e o StorageClient NÃO é tocado dentro do use case (o job apaga fora da tx, após o commit).
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Testcontainers
class DeletePrivateCollectionForUserIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired DeletePrivateCollectionForUserUseCase useCase;
    @Autowired MangaRepository mangaRepository;
    @Autowired VolumeRepository volumeRepository;
    @Autowired UserRepository userRepository;
    @MockBean StorageClient storageClient;

    User owner;

    @BeforeEach
    void setUp() {
        volumeRepository.deleteAllInBatch();
        mangaRepository.deleteAll();
        userRepository.deleteAllInBatch();

        owner = userRepository.save(User.register(
                Email.of("owner@delete.test"), Username.of("deleteOwner"),
                "$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC", "test",
                Quota.of(BigDecimal.ONE)));
    }

    @Test
    void deletesPrivateMangasAndVolumes_returnsObjectNames_keepsPublic_whenUserHasCollection() {
        // 2 mangás privados (com capa + 2 volumes cada) + 1 público do mesmo dono
        Manga privateA = privateMangaWithVolumes("Private A", "cover-a", "vol-a1", "vol-a2");
        Manga privateB = privateMangaWithVolumes("Private B", "cover-b", "vol-b1", "vol-b2");
        Manga publicManga = publicMangaWithVolume("Public C", "cover-c", "vol-c1");

        UUID privateAId = privateA.getId();
        UUID privateBId = privateB.getId();
        UUID publicId = publicManga.getId();

        List<String> objectNames = useCase.handle(owner.getId());

        // 1. Object names retornados batem exatamente com os arquivos dos privados (capas + volumes)
        assertThat(objectNames).containsExactlyInAnyOrder(
                "cover-a", "vol-a1", "vol-a2",
                "cover-b", "vol-b1", "vol-b2");

        // 2. Mangás privados + seus volumes sumiram do banco
        assertThat(mangaRepository.findById(privateAId)).isEmpty();
        assertThat(mangaRepository.findById(privateBId)).isEmpty();
        assertThat(volumeRepository.findByMangaId(privateAId)).isEmpty();
        assertThat(volumeRepository.findByMangaId(privateBId)).isEmpty();

        // 3. Mangá público (e seu volume) permaneceram intactos
        assertThat(mangaRepository.findById(publicId)).isPresent();
        assertThat(volumeRepository.findByMangaId(publicId)).hasSize(1);

        // 4. O StorageClient NÃO é chamado dentro do use case — o GCS é apagado fora da tx
        verifyNoInteractions(storageClient);
    }

    @Test
    void returnsEmptyList_whenUserHasNoPrivateCollection() {
        publicMangaWithVolume("Only Public", "cover-x", "vol-x1");

        List<String> objectNames = useCase.handle(owner.getId());

        assertThat(objectNames).isEmpty();
        verifyNoInteractions(storageClient);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Manga privateMangaWithVolumes(String title, String coverName, String... volumeObjectNames) {
        Manga manga = Manga.createPrivate(uniqueSlug(title), title, "synopsis", owner.getId());
        manga.changeCover(coverName);
        addVolumes(manga, volumeObjectNames);
        return mangaRepository.save(manga);
    }

    private Manga publicMangaWithVolume(String title, String coverName, String... volumeObjectNames) {
        Manga manga = Manga.createPublic(uniqueSlug(title), owner.getId());
        manga.updateCatalogDetails(title, List.of(), "synopsis",
                com.buruna.manga.domain.MangaFormat.MANGA, null,
                com.buruna.manga.domain.MangaStatusOrigin.ONGOING,
                com.buruna.manga.domain.MangaStatusSite.INCOMPLETE, null, List.of(), java.util.Set.of());
        manga.changeCover(coverName);
        addVolumes(manga, volumeObjectNames);
        return mangaRepository.save(manga);
    }

    private void addVolumes(Manga manga, String... volumeObjectNames) {
        int number = 1;
        for (String objectName : volumeObjectNames) {
            manga.addVolume(VolumeNumber.of(number),
                    objectName, FileHash.of("hash-" + objectName), 1024L, owner.getId());
            number++;
        }
    }

    private static Slug uniqueSlug(String title) {
        return Slug.of(Slug.fromTitle(title).value() + "-" + UUID.randomUUID());
    }
}
