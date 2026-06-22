package com.buruna.reading;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.domain.MangaStatusSite;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.reading.persistence.ReadingHistoryRepository;
import com.buruna.reading.persistence.ReadingProgressRepository;
import com.buruna.shared.storage.StorageClient;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rede de segurança do Epic 2: reproduz em JUnit os cenários do test-phase6.sh
 * (URL assinada, progresso, histórico), cobrindo acesso, isolamento por usuário,
 * view_count e paginação. Substituição 1:1 conforme ADR-38.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class ReadingIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired MangaRepository mangaRepository;
    @Autowired VolumeRepository volumeRepository;
    @Autowired ReadingHistoryRepository historyRepository;
    @Autowired ReadingProgressRepository progressRepository;
    @MockBean StorageClient storageClient;

    User readerA;
    User readerB;
    Manga publicManga;
    Manga privateManga;
    Volume publicVol1;
    Volume publicVol2;
    Volume privateVol;

    static final URL FAKE_URL;
    static {
        try {
            FAKE_URL = new URL("https://storage.example.com/signed?token=test");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        when(storageClient.generateSignedUrl(anyString(), any(Duration.class))).thenReturn(FAKE_URL);

        historyRepository.deleteAllInBatch();
        progressRepository.deleteAllInBatch();
        volumeRepository.deleteAllInBatch();
        mangaRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        readerA = userRepository.save(buildUser("readerA@reading.test", "readReaderA"));
        readerB = userRepository.save(buildUser("readerB@reading.test", "readReaderB"));

        publicManga  = mangaRepository.save(buildManga("reading-public-manga",  "Reading Test Manga",    true,  readerA));
        privateManga = mangaRepository.save(buildManga("reading-private-manga", "Reading Private Manga", false, readerA));

        publicVol1 = volumeRepository.save(buildVolume(publicManga,  1, "test/pub_v1.pdf",  readerA));
        publicVol2 = volumeRepository.save(buildVolume(publicManga,  2, "test/pub_v2.pdf",  readerA));
        privateVol = volumeRepository.save(buildVolume(privateManga, 1, "test/priv_v1.pdf", readerA));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        ));
    }

    static User buildUser(String email, String username) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash("$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC");
        u.setPresentationMessage("test");
        u.setRole(Role.READER);
        u.setStatus(UserStatus.ACTIVE);
        u.setQuotaGb(BigDecimal.ONE);
        u.setTotpEnabled(false);
        return u;
    }

    static Manga buildManga(String slug, String title, boolean isPublic, User owner) {
        Manga m = new Manga();
        m.setSlug(slug);
        m.setTitle(title);
        m.setFormat(MangaFormat.MANGA);
        m.setStatusOrigin(MangaStatusOrigin.ONGOING);
        m.setStatusSite(MangaStatusSite.INCOMPLETE);
        m.setPublic(isPublic);
        m.setOwner(owner);
        return m;
    }

    static Volume buildVolume(Manga manga, int volumeNumber, String fileUrl, User uploadedBy) {
        Volume v = new Volume();
        v.setManga(manga);
        v.setVolumeNumber(volumeNumber);
        v.setFileUrl(fileUrl);
        v.setFileHash("hash-" + UUID.randomUUID());
        v.setFileSizeBytes(1024L);
        v.setUploadedBy(uploadedBy);
        return v;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  1. GET /reader/{volumeId}/url
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getVolumeUrl_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/reader/{id}/url", publicVol1.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getVolumeUrl_authenticated_returns200WithUrlAndExpiry() throws Exception {
        mockMvc.perform(get("/reader/{id}/url", publicVol1.getId()).with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(FAKE_URL.toString()))
                .andExpect(jsonPath("$.expiresInSeconds").value(1800));
    }

    @Test
    void getVolumeUrl_incrementsViewCount() throws Exception {
        int before = mangaRepository.findById(publicManga.getId()).orElseThrow().getViewCount();

        mockMvc.perform(get("/reader/{id}/url", publicVol1.getId()).with(auth(readerB)))
                .andExpect(status().isOk());

        int after = mangaRepository.findById(publicManga.getId()).orElseThrow().getViewCount();
        org.assertj.core.api.Assertions.assertThat(after).isGreaterThan(before);
    }

    @Test
    void getVolumeUrl_nonexistentVolume_returns404() throws Exception {
        mockMvc.perform(get("/reader/{id}/url", UUID.randomUUID()).with(auth(readerA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVolumeUrl_privateVolumeByNonOwner_returns403() throws Exception {
        mockMvc.perform(get("/reader/{id}/url", privateVol.getId()).with(auth(readerB)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getVolumeUrl_privateVolumeByOwner_returns200() throws Exception {
        mockMvc.perform(get("/reader/{id}/url", privateVol.getId()).with(auth(readerA)))
                .andExpect(status().isOk());
    }

    @Test
    void getVolumeUrl_registersReadingHistoryEntry() throws Exception {
        mockMvc.perform(get("/reader/{id}/url", publicVol1.getId()).with(auth(readerA)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/reader/history").with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  2. POST /reader/{volumeId}/progress
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void saveProgress_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/reader/{id}/progress", publicVol1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPage\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void saveProgress_savesCurrentPage() throws Exception {
        mockMvc.perform(post("/reader/{id}/progress", publicVol1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPage\":10}")
                        .with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(10));
    }

    @Test
    void saveProgress_upsert_updatesPage() throws Exception {
        mockMvc.perform(post("/reader/{id}/progress", publicVol1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPage\":10}").with(auth(readerA)));

        mockMvc.perform(post("/reader/{id}/progress", publicVol1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPage\":25}")
                        .with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(25));
    }

    @Test
    void saveProgress_pageZero_returns400() throws Exception {
        mockMvc.perform(post("/reader/{id}/progress", publicVol1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPage\":0}")
                        .with(auth(readerA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveProgress_nonexistentVolume_returns404() throws Exception {
        mockMvc.perform(post("/reader/{id}/progress", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPage\":1}")
                        .with(auth(readerA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveProgress_isolatedByUser_eachUserHasOwnPage() throws Exception {
        mockMvc.perform(post("/reader/{id}/progress", publicVol1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPage\":25}").with(auth(readerA)));

        mockMvc.perform(post("/reader/{id}/progress", publicVol1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPage\":5}")
                        .with(auth(readerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(5));

        // readerA still has page 25
        mockMvc.perform(get("/reader/{id}/progress", publicVol1.getId()).with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(25));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  3. GET /reader/progress/{mangaId}
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getProgress_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/reader/progress/{id}", publicManga.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProgress_returnsCurrentPage() throws Exception {
        mockMvc.perform(post("/reader/{id}/progress", publicVol1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPage\":25}").with(auth(readerA)));

        mockMvc.perform(get("/reader/progress/{id}", publicManga.getId()).with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(25));
    }

    @Test
    void getProgress_returnsVolumeWithHighestNumber() throws Exception {
        mockMvc.perform(post("/reader/{id}/progress", publicVol1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPage\":5}").with(auth(readerA)));
        mockMvc.perform(post("/reader/{id}/progress", publicVol2.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPage\":3}").with(auth(readerA)));

        mockMvc.perform(get("/reader/progress/{id}", publicManga.getId()).with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeId").value(publicVol2.getId().toString()));
    }

    @Test
    void getProgress_neverRead_returns204() throws Exception {
        mockMvc.perform(get("/reader/progress/{id}", publicManga.getId()).with(auth(readerA)))
                .andExpect(status().isNoContent());
    }

    @Test
    void getProgress_nonexistentManga_returns404() throws Exception {
        mockMvc.perform(get("/reader/progress/{id}", UUID.randomUUID()).with(auth(readerA)))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  4. GET /reader/history
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getHistory_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/reader/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getHistory_empty_returnsEmptyContent() throws Exception {
        mockMvc.perform(get("/reader/history").with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getHistory_afterReading_containsEntryWithRequiredFields() throws Exception {
        mockMvc.perform(get("/reader/{id}/url", publicVol1.getId()).with(auth(readerA)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/reader/history").with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].volumeId").exists())
                .andExpect(jsonPath("$.content[0].volumeNumber").exists())
                .andExpect(jsonPath("$.content[0].mangaTitle").exists())
                .andExpect(jsonPath("$.content[0].readAt").exists());
    }

    @Test
    void getHistory_isolatedByUser() throws Exception {
        // readerA reads 2 volumes; readerB reads 1
        mockMvc.perform(get("/reader/{id}/url", publicVol1.getId()).with(auth(readerA)));
        mockMvc.perform(get("/reader/{id}/url", publicVol2.getId()).with(auth(readerA)));
        mockMvc.perform(get("/reader/{id}/url", publicVol1.getId()).with(auth(readerB)));

        mockMvc.perform(get("/reader/history").with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/reader/history").with(auth(readerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void getHistory_pagination_sizeOneReturnsOneItem() throws Exception {
        mockMvc.perform(get("/reader/{id}/url", publicVol1.getId()).with(auth(readerA)));
        mockMvc.perform(get("/reader/{id}/url", publicVol2.getId()).with(auth(readerA)));

        mockMvc.perform(get("/reader/history").param("size", "1").param("page", "0").with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }
}
