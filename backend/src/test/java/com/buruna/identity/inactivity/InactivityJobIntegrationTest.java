package com.buruna.identity.inactivity;

import com.buruna.identity.application.admin.RunInactivityUseCase;
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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rede de segurança do Epic 5: cobre o {@link RunInactivityUseCase} (job de inatividade) e o
 * dashboard de admin. Portou os cenários do {@code scripts/test-phase8.sh} em [5.1]; a partir
 * de [5.3] reflete o COMPORTAMENTO CORRETO após o conserto dos bugs B1/B2 e a injeção do
 * {@code Clock} (ADR-36).
 *
 * <h2>Tempo determinístico via {@code Clock.fixed}</h2>
 * O use case obtém "agora" de um {@link Clock} injetado. Aqui esse clock é fixado em
 * {@link #FIXED_NOW}, então os limiares 75/90 podem ser testados nas bordas EXATAS (sem a
 * folga de ~1 dia que a versão pré-Clock de [5.1] precisava).
 *
 * <h2>Limiares (EXCLUSIVOS na borda, ver {@code InactivityPolicy})</h2>
 * <ul>
 *   <li>inatividade &le; 75 dias &rarr; nada;</li>
 *   <li>75 &lt; inatividade &le; 90 dias &rarr; aviso (usuário permanece {@code ACTIVE});</li>
 *   <li>inatividade &gt; 90 dias &rarr; desativação + coleção privada apagada + limpeza GCS.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class InactivityJobIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /** Instante fixo do relógio do use case, para bordas de limiar determinísticas. */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-04T02:00:00Z");
    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    /** Hash BCrypt de teste (mesmo usado nas demais integrações); valor irrelevante para o job. */
    private static final String PASSWORD_HASH =
            "$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC";

    /** 200 MB — volume privado usado para o storage do dashboard e a limpeza na desativação. */
    private static final long PRIVATE_VOLUME_BYTES = 209_715_200L;
    /** 500 MB — volume público, que NÃO deve contar no storage do dashboard. */
    private static final long PUBLIC_VOLUME_BYTES = 524_288_000L;

    @Autowired RunInactivityUseCase runInactivityUseCase;
    @Autowired UserRepository userRepository;
    @Autowired MangaRepository mangaRepository;
    @Autowired VolumeRepository volumeRepository;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    /** Substituídos por mocks: o job só deve avisar (e-mail) e apagar GCS conforme o limiar. */
    @MockBean EmailService emailService;
    @MockBean StorageClient storageClient;

    /** Relógio fixo (ADR-36): torna as bordas 75/90 determinísticas. */
    @MockBean Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

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
        void doesNothing_whenInactiveExactly75Days() {
            User user = activeUser("keep@inactivity.test", "keepUser", 75);

            runInactivityUseCase.run();

            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
            verifyNoInteractions(emailService);
            verifyNoInteractions(storageClient);
        }

        @Test
        void doesNothing_whenNeverLoggedInButRecentlyCreated() {
            // last_access_at nulo com created_at recente: não cruzou o limiar → fora da seleção.
            User user = User.register(Email.of("fresh@inactivity.test"), Username.of("freshUser"),
                    PASSWORD_HASH, "test", Quota.of(new BigDecimal("2.00")));
            user.changeStatus(UserStatus.ACTIVE);
            userRepository.save(user);

            runInactivityUseCase.run();

            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
            verifyNoInteractions(emailService);
            verifyNoInteractions(storageClient);
        }

        @Test
        void sendsWarning_staysActive_whenInactive76Days() {
            User user = activeUser("warn76@inactivity.test", "warn76User", 76);

            runInactivityUseCase.run();

            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(emailService, times(1))
                    .sendInactivityWarning("warn76@inactivity.test", "warn76User");
            verifyNoInteractions(storageClient);
        }

        @Test
        void sendsWarning_staysActive_whenInactiveExactly90Days() {
            // Borda exata: 90 dias ainda é aviso; só passa a desativação ACIMA de 90.
            User user = activeUser("warn90@inactivity.test", "warn90User", 90);

            runInactivityUseCase.run();

            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(emailService, times(1))
                    .sendInactivityWarning("warn90@inactivity.test", "warn90User");
            verifyNoInteractions(storageClient);
        }

        @Test
        void deactivates_whenInactiveJustPast90Days() {
            // Um segundo além de 90 dias já cai na desativação — a borda ficou exata com o Clock.
            User user = activeUserAt("edge@inactivity.test", "edgeUser",
                    FIXED_NOW.minusDays(90).minusSeconds(1));

            runInactivityUseCase.run();

            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.INACTIVE);
            verify(emailService, never()).sendInactivityWarning(user.getEmail(), user.getUsername());
        }

        @Test
        void deactivatesCleanly_whenInactive91Days_withoutPrivateCollection() {
            User user = activeUser("gone91@inactivity.test", "gone91User", 91);

            runInactivityUseCase.run();

            // Sem coleção privada, a desativação não toca GCS.
            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.INACTIVE);
            verify(emailService, never()).sendInactivityWarning(user.getEmail(), user.getUsername());
            verifyNoInteractions(storageClient);
        }

        /**
         * B1 CORRIGIDO: desativar um usuário COM coleção privada agora funciona.
         *
         * <p>Antes de [5.3], {@code deactivateUser} era {@code @Transactional} mas chamado por
         * self-invocation, então {@code manga.getVolumes()} rodava sem sessão e lançava
         * {@code LazyInitializationException} — o usuário permanecia {@code ACTIVE}. Agora a
         * coleção privada é apagada por {@code DeletePrivateCollectionForUserUseCase} (transação
         * própria de {@code manga}, proxy AOP válido): os volumes são lidos dentro dessa tx, o
         * usuário é desativado e os arquivos são removidos do GCS depois (best-effort).
         */
        @Test
        void deactivatesAndDeletesCollection_whenInactive91Days_withPrivateCollection() {
            User user = activeUser("wipe91@inactivity.test", "wipe91User", 91);
            Manga privateManga = privateMangaWith(user, "wipe-cover", "wipe-vol-1");
            UUID mangaId = privateManga.getId();

            runInactivityUseCase.run();

            assertThat(reload(user).getStatus()).isEqualTo(UserStatus.INACTIVE);
            // Coleção privada apagada do banco (mangá + volumes via cascade do agregado).
            assertThat(mangaRepository.findById(mangaId)).isEmpty();
            assertThat(volumeRepository.findByMangaId(mangaId)).isEmpty();
            // GCS limpo com os object names retornados pelo use case de manga (capa + volume).
            verify(storageClient, times(1)).delete("wipe-cover");
            verify(storageClient, times(1)).delete("wipe-vol-1");
            verify(emailService, never()).sendInactivityWarning(user.getEmail(), user.getUsername());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Regressão do B2 — paginação sobre conjunto mutável pulava usuários
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Batch {

        /**
         * B2 CORRIGIDO: um lote grande (acima do antigo tamanho de página de 50) em que TODOS
         * são desativados. A versão antiga paginava por offset sobre o conjunto {@code ACTIVE};
         * ao desativar a primeira página, os usuários saíam do conjunto e os offsets seguintes
         * pulavam o restante. Aqui provamos que NENHUM usuário elegível é pulado.
         */
        @Test
        void deactivatesEveryEligibleUser_withoutSkipping_inLargeBatch() {
            int total = 55; // > 50 (tamanho da página antiga) para expor o pulo do B2
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                User user = activeUser("batch" + i + "@inactivity.test", "batchUser" + i, 91);
                ids.add(user.getId());
            }
            // Alguns com coleção privada, para exercitar o fix do B1 em escala no mesmo lote.
            privateMangaWith(userRepository.findById(ids.get(0)).orElseThrow(),
                    "batch-cover-0", "batch-vol-0");
            privateMangaWith(userRepository.findById(ids.get(total - 1)).orElseThrow(),
                    "batch-cover-last", "batch-vol-last");

            runInactivityUseCase.run();

            long stillActive = ids.stream()
                    .filter(id -> userRepository.findById(id).orElseThrow().getStatus() == UserStatus.ACTIVE)
                    .count();
            assertThat(stillActive).as("nenhum usuário elegível pode ser pulado").isZero();
        }

        /**
         * Lote misto: alguns são avisados e outros desativados numa única execução, provando
         * que ambos os caminhos coexistem sem interferência.
         */
        @Test
        void warnsAndDeactivates_inSingleRun() {
            User toWarn = activeUser("mixWarn@inactivity.test", "mixWarn", 80);
            User toDeactivate = activeUser("mixGone@inactivity.test", "mixGone", 95);

            runInactivityUseCase.run();

            assertThat(reload(toWarn).getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(emailService, times(1)).sendInactivityWarning("mixWarn@inactivity.test", "mixWarn");
            assertThat(reload(toDeactivate).getStatus()).isEqualTo(UserStatus.INACTIVE);
            verify(emailService, never())
                    .sendInactivityWarning("mixGone@inactivity.test", "mixGone");
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

    /** Cria e persiste um usuário ACTIVE cujo último acesso foi há {@code daysAgo} dias (relativo ao Clock fixo). */
    private User activeUser(String email, String username, int daysAgo) {
        return activeUserAt(email, username, FIXED_NOW.minusDays(daysAgo));
    }

    /** Cria e persiste um usuário ACTIVE com {@code last_access_at} exato. */
    private User activeUserAt(String email, String username, OffsetDateTime lastAccess) {
        User user = User.register(Email.of(email), Username.of(username),
                PASSWORD_HASH, "test", Quota.of(new BigDecimal("2.00")));
        user.changeStatus(UserStatus.ACTIVE);
        user.recordLogin(lastAccess);
        return userRepository.save(user);
    }

    private User adminUser(String email, String username) {
        User user = User.register(Email.of(email), Username.of(username),
                PASSWORD_HASH, "test", Quota.of(new BigDecimal("2.00")));
        user.changeRole(Role.ADMIN);
        user.changeStatus(UserStatus.ACTIVE);
        user.recordLogin(FIXED_NOW.minusDays(1));
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
