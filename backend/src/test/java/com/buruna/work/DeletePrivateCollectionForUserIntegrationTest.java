package com.buruna.work;

import com.buruna.work.application.maintenance.DeletePrivateCollectionForUserUseCase;
import com.buruna.work.domain.FileHash;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.Slug;
import com.buruna.work.domain.VolumeNumber;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.work.persistence.VolumeRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    @Autowired WorkRepository workRepository;
    @Autowired VolumeRepository volumeRepository;
    @Autowired UserRepository userRepository;
    @MockitoBean StorageClient storageClient;

    User owner;

    @BeforeEach
    void setUp() {
        volumeRepository.deleteAllInBatch();
        workRepository.deleteAll();
        userRepository.deleteAllInBatch();

        owner = userRepository.save(User.register(
                Email.of("owner@delete.test"), Username.of("deleteOwner"),
                "$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC", "test",
                Quota.of(BigDecimal.ONE)));
    }

    @Test
    void deletesPrivateWorksAndVolumes_returnsObjectNames_keepsPublic_whenUserHasCollection() {
        // 2 mangás privados (com capa + 2 volumes cada) + 1 público do mesmo dono
        Work privateA = privateWorkWithVolumes("Private A", "cover-a", "vol-a1", "vol-a2");
        Work privateB = privateWorkWithVolumes("Private B", "cover-b", "vol-b1", "vol-b2");
        Work publicWork = publicWorkWithVolume("Public C", "cover-c", "vol-c1");

        UUID privateAId = privateA.getId();
        UUID privateBId = privateB.getId();
        UUID publicId = publicWork.getId();

        List<String> objectNames = useCase.handle(owner.getId());

        // 1. Object names retornados batem exatamente com os arquivos dos privados (capas + volumes)
        assertThat(objectNames).containsExactlyInAnyOrder(
                "cover-a", "vol-a1", "vol-a2",
                "cover-b", "vol-b1", "vol-b2");

        // 2. Mangás privados + seus volumes sumiram do banco
        assertThat(workRepository.findById(privateAId)).isEmpty();
        assertThat(workRepository.findById(privateBId)).isEmpty();
        assertThat(volumeRepository.findByWorkId(privateAId)).isEmpty();
        assertThat(volumeRepository.findByWorkId(privateBId)).isEmpty();

        // 3. Mangá público (e seu volume) permaneceram intactos
        assertThat(workRepository.findById(publicId)).isPresent();
        assertThat(volumeRepository.findByWorkId(publicId)).hasSize(1);

        // 4. O StorageClient NÃO é chamado dentro do use case — o GCS é apagado fora da tx
        verifyNoInteractions(storageClient);
    }

    @Test
    void returnsEmptyList_whenUserHasNoPrivateCollection() {
        publicWorkWithVolume("Only Public", "cover-x", "vol-x1");

        List<String> objectNames = useCase.handle(owner.getId());

        assertThat(objectNames).isEmpty();
        verifyNoInteractions(storageClient);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Work privateWorkWithVolumes(String title, String coverName, String... volumeObjectNames) {
        Work work = Work.createPrivate(uniqueSlug(title), title, "synopsis", owner.getId());
        work.changeCover(coverName);
        addVolumes(work, volumeObjectNames);
        return workRepository.save(work);
    }

    private Work publicWorkWithVolume(String title, String coverName, String... volumeObjectNames) {
        Work work = Work.createPublic(uniqueSlug(title), owner.getId());
        work.updateCatalogDetails(title, List.of(), "synopsis",
                com.buruna.work.domain.WorkFormat.MANGA, null,
                com.buruna.work.domain.WorkStatusOrigin.ONGOING,
                com.buruna.work.domain.WorkStatusSite.INCOMPLETE, null, List.of(), java.util.Set.of());
        work.changeCover(coverName);
        addVolumes(work, volumeObjectNames);
        return workRepository.save(work);
    }

    private void addVolumes(Work work, String... volumeObjectNames) {
        int number = 1;
        for (String objectName : volumeObjectNames) {
            work.addVolume(VolumeNumber.of(number),
                    objectName, FileHash.of("hash-" + objectName), 1024L, owner.getId());
            number++;
        }
    }

    private static Slug uniqueSlug(String title) {
        return Slug.of(Slug.fromTitle(title).value() + "-" + UUID.randomUUID());
    }
}
