package com.buruna.identity.inactivity;

import com.buruna.identity.application.admin.InactivityJob;
import com.buruna.identity.domain.Email;
import com.buruna.identity.domain.Quota;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.domain.Username;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.manga.domain.FileHash;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.domain.MangaStatusSite;
import com.buruna.manga.domain.Slug;
import com.buruna.manga.domain.VolumeNumber;
import com.buruna.manga.persistence.MangaRepository;
import com.buruna.manga.persistence.VolumeRepository;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.storage.StorageClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rede de segurança do Epic 5 [5.1]: reproduz em JUnit os cenários do
 * {@code scripts/test-phase8.sh} (dashboard de admin + job de inatividade) antes de
 * refatorar o job em [5.3]. Este teste captura o COMPORTAMENTO ATUAL do
 * {@link InactivityJob} — não o comportamento desejado. Os bugs B1 (self-invocation do
 * {@code @Transactional}) e B2 (paginação por offset sobre conjunto mutável) permanecem
 * intocados; suas regressões são cobertas em [5.3], não aqui.
 *
 * <h2>Determinismo sem {@code Clock}</h2>
 * Hoje o job usa {@code OffsetDateTime.now()} direto (não o {@code Clock} do ADR-36),
 * então NÃO é possível congelar o tempo. A rede de segurança contorna isso ancorando o
 * {@code last_access_at} de cada usuário em {@code now() - N dias} no momento do setup,
 * escolhendo N com folga de ~1 dia dos limiares (74/76/89/91 em vez de exatos 75/90).
 * Como o job recalcula seu próprio {@code now()} milissegundos depois, a folga garante
 * que a classificação (NONE/WARN/DEACTIVATE) é estável em qualquer horário de execução.
 *
 * <p><b>O que só fica determinístico após [5.3]:</b> os casos de fronteira EXATOS
 * (exatamente 75 ou exatamente 90 dias) e qualquer asserção sensível a fração de dia
 * dependem da injeção do {@code Clock.fixed(...)} — fora do escopo desta issue. Aqui
 * cobrimos apenas os degraus com folga, que é o que o {@code test-phase8.sh} também fazia
 * (80 e 95 dias).
 *
 * <h2>Trilha "desativação com coleção privada" está PINADA como bug (B1)</h2>
 * Hoje o job NÃO consegue desativar um usuário que tenha coleção privada: o B1
 * (self-invocation do {@code @Transactional} em {@code deactivateUser}) deixa
 * {@code manga.getVolumes()} sem sessão e lança {@link LazyInitializationException}. O teste
 * {@code deactivationWithPrivateCollection_currentlyFailsDueToB1} captura fielmente esse crash;
 * a trilha vira verde (desativa + apaga coleção + GCS) só depois do fix do B1 em [5.3], quando
 * este teste é reescrito. Consertar B1/B2 e injetar o {@code Clock} são escopo de [5.3], não aqui.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class InactivityJobIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /** Hash BCrypt de teste (mesmo usado nas demais integrações); valor irrelevante para o job. */
    private static final String PASSWORD_HASH =
            "$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC";

    /** 200 MB — volume privado usado para o storage do dashboard e a limpeza na desativação. */
    private static final long PRIVATE_VOLUME_BYTES = 209_715_200L;
    /** 500 MB — volume público, que NÃO deve contar no storage do dashboard. */
    private static final long PUBLIC_VOLUME_BYTES = 524_288_000L;

    @Autowired InactivityJob inactivityJob;
    @Autowired UserRepository userRepository;
    @Autowired MangaRepository mangaRepository;
    @Autowired VolumeRepository volumeRepository;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    /** Substituídos por mocks: o job só deve avisar (e-mail) e apagar GCS conforme o limiar. */
    @MockBean EmailService emailService;
    @MockBean StorageClient storageClient;

    @BeforeEach
    void setUp() {
        volumeRepository.deleteAllInBatch();
        mangaRepository.deleteAll();
        userRepository.deleteAllInBatch();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Job de inatividade — limiares 75 (aviso) / 90 (desativação)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class InactivityThresholds {

        @Test
        void doesNothing_whenUserInactive74Days() {
            User user = activeUser("keep@inactivity.test", "keepUser", 74);

            inactivityJob.runJob();

            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
            verifyNoInteractions(emailService);
            verifyNoInteractions(storageClient);
        }

        @Test
        void sendsWarning_staysActive_whenUserInactive76Days() {
            User user = activeUser("warn76@inactivity.test", "warn76User", 76);

            inactivityJob.runJob();

            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(emailService, times(1))
                    .sendInactivityWarning("warn76@inactivity.test", "warn76User");
            verifyNoInteractions(storageClient);
        }

        @Test
        void sendsWarning_staysActive_whenUserInactive89Days() {
            User user = activeUser("warn89@inactivity.test", "warn89User", 89);

            inactivityJob.runJob();

            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(emailService, times(1))
                    .sendInactivityWarning("warn89@inactivity.test", "warn89User");
            verifyNoInteractions(storageClient);
        }

        @Test
        void deactivatesCleanly_whenUserInactive91Days_withoutPrivateCollection() {
            User user = activeUser("gone91@inactivity.test", "gone91User", 91);

            inactivityJob.runJob();

            // Sem coleção privada, a desativação não toca lazy collections: passa hoje.
            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.INACTIVE);
            verify(emailService, never()).sendInactivityWarning(user.getEmail(), user.getUsername());
            verifyNoInteractions(storageClient);
        }

        /**
         * PIN do bug B1: HOJE, desativar um usuário que TEM coleção privada quebra.
         *
         * <p>{@code deactivateUser} é {@code @Transactional}, mas {@code runJob()} o chama por
         * self-invocation (B1) — o proxy AOP não aplica a transação. Com {@code open-in-view=false}
         * e {@code Manga.volumes} LAZY, {@code manga.getVolumes().forEach(...)} roda sem sessão
         * Hibernate e lança {@link LazyInitializationException}. O job aborta: o usuário permanece
         * {@code ACTIVE}, a coleção intacta e o GCS não é tocado.
         *
         * <p>Esta é a captura fiel do comportamento atual, NÃO o desejado. O [5.3] corrige o B1
         * (transação por usuário via bean/proxy válido) e esta trilha passa a desativar o usuário
         * e apagar a coleção — momento em que este teste é reescrito para o verde. Consertar o B1
         * aqui está fora do escopo do [5.1].
         */
        @Test
        void deactivationWithPrivateCollection_currentlyFailsDueToB1() {
            User user = activeUser("crash91@inactivity.test", "crash91User", 91);
            Manga privateManga = privateMangaWith(user, "crash-cover", "crash-vol-1");
            UUID mangaId = privateManga.getId();

            assertThatThrownBy(() -> inactivityJob.runJob())
                    .isInstanceOf(LazyInitializationException.class);

            // O job abortou antes de mutar qualquer coisa: nada mudou.
            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(mangaRepository.findById(mangaId)).isPresent();
            assertThat(volumeRepository.findByMangaId(mangaId)).hasSize(1);
            verifyNoInteractions(storageClient);
            verifyNoInteractions(emailService);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Dashboard — GET /admin/dashboard
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class AdminDashboard {

        @Test
        void returns401_whenNoToken() throws Exception {
            mockMvc.perform(get("/admin/dashboard"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void returns403_whenNonAdmin() throws Exception {
            User reader = activeUser("reader@dash.test", "dashReader", 1);

            mockMvc.perform(get("/admin/dashboard").with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void reportsActiveUsersAndPrivateStorageOnly_whenAdmin() throws Exception {
            User admin = adminUser("admin@dash.test", "dashAdmin");
            User owner = activeUser("owner@dash.test", "dashOwner", 1);
            // Um INACTIVE para provar que activeUsers não o conta.
            inactiveUser("inactive@dash.test", "dashInactive");

            // Owner tem 200MB privado + 500MB público; o dashboard só deve contar o privado.
            privateMangaWith(owner, "dash-priv-cover", "dash-priv-vol");
            publicMangaWith(owner, "dash-pub-cover", "dash-pub-vol");

            MvcResult result = mockMvc.perform(get("/admin/dashboard").with(auth(admin)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

            // activeUsers = admin + owner (o INACTIVE fica de fora).
            assertThat(body.get("activeUsers").asLong())
                    .isEqualTo(userRepository.countByStatus(UserStatus.ACTIVE))
                    .isEqualTo(2L);

            // 200 MB / 1 GiB = 0.1953125 → arredonda para 0.20 (HALF_UP, 2 casas). Sem o público.
            assertThat(body.get("totalStorageUsedGb").decimalValue())
                    .isEqualByComparingTo(new BigDecimal("0.20"));

            // storageByUser contém o owner com o mesmo storage privado (público excluído).
            JsonNode ownerEntry = storageEntryFor(body, "dashOwner");
            assertThat(ownerEntry).isNotNull();
            assertThat(ownerEntry.get("userId").asText()).isEqualTo(owner.getId().toString());
            assertThat(ownerEntry.get("usedGb").decimalValue())
                    .isEqualByComparingTo(new BigDecimal("0.20"));
            assertThat(ownerEntry.get("quotaGb").decimalValue())
                    .isEqualByComparingTo(new BigDecimal("2.00"));
        }

        private JsonNode storageEntryFor(JsonNode body, String username) {
            for (JsonNode entry : body.get("storageByUser")) {
                if (username.equals(entry.get("username").asText())) {
                    return entry;
                }
            }
            return null;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Cria e persiste um usuário ACTIVE cujo último acesso foi há {@code daysAgo} dias. */
    private User activeUser(String email, String username, int daysAgo) {
        User user = User.register(Email.of(email), Username.of(username),
                PASSWORD_HASH, "test", Quota.of(new BigDecimal("2.00")));
        user.changeStatus(UserStatus.ACTIVE);
        user.recordLogin(OffsetDateTime.now().minusDays(daysAgo));
        return userRepository.save(user);
    }

    private User adminUser(String email, String username) {
        User user = User.register(Email.of(email), Username.of(username),
                PASSWORD_HASH, "test", Quota.of(new BigDecimal("2.00")));
        user.changeRole(Role.ADMIN);
        user.changeStatus(UserStatus.ACTIVE);
        user.recordLogin(OffsetDateTime.now().minusDays(1));
        return userRepository.save(user);
    }

    private User inactiveUser(String email, String username) {
        User user = User.register(Email.of(email), Username.of(username),
                PASSWORD_HASH, "test", Quota.of(new BigDecimal("2.00")));
        user.changeStatus(UserStatus.INACTIVE);
        return userRepository.save(user);
    }

    private Manga privateMangaWith(User owner, String coverName, String volumeObjectName) {
        Manga manga = Manga.createPrivate(uniqueSlug("private"), "Private", "synopsis", owner.getId());
        manga.changeCover(coverName);
        manga.addVolume(VolumeNumber.of(1), volumeObjectName,
                FileHash.of("hash-" + volumeObjectName), PRIVATE_VOLUME_BYTES, owner.getId());
        return mangaRepository.save(manga);
    }

    private Manga publicMangaWith(User owner, String coverName, String volumeObjectName) {
        Manga manga = Manga.createPublic(uniqueSlug("public"), owner.getId());
        manga.updateCatalogDetails("Public", List.of(), "synopsis",
                MangaFormat.MANGA, null,
                MangaStatusOrigin.ONGOING, MangaStatusSite.INCOMPLETE, null, List.of(), java.util.Set.of());
        manga.changeCover(coverName);
        manga.addVolume(VolumeNumber.of(1), volumeObjectName,
                FileHash.of("hash-" + volumeObjectName), PUBLIC_VOLUME_BYTES, owner.getId());
        return mangaRepository.save(manga);
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private static Slug uniqueSlug(String prefix) {
        return Slug.of(prefix + "-" + UUID.randomUUID());
    }

    private RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }
}
