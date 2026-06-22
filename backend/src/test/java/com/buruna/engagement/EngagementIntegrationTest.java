package com.buruna.engagement;

import com.buruna.engagement.persistence.RatingRepository;
import com.buruna.engagement.persistence.ReadingListRepository;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.domain.MangaStatusSite;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rede de segurança do Epic 1: reproduz em JUnit os cenários do test-phase7.sh
 * (ratings + reading-list), cobrindo CRUD, recálculo de avgRating, unicidade e
 * isolamento por usuário. Substituição 1:1 conforme ADR-38.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class EngagementIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired MangaRepository mangaRepository;
    @Autowired RatingRepository ratingRepository;
    @Autowired ReadingListRepository readingListRepository;

    User readerA;
    User readerB;
    Manga manga1;
    Manga manga2;

    @BeforeEach
    void setUp() {
        ratingRepository.deleteAllInBatch();
        readingListRepository.deleteAllInBatch();
        mangaRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        readerA = userRepository.save(buildUser("readerA@eng.test", "engReaderA", Role.READER));
        readerB = userRepository.save(buildUser("readerB@eng.test", "engReaderB", Role.READER));
        manga1 = mangaRepository.save(buildPublicManga("eng-test-manga-alpha", "Eng Test Manga Alpha", readerA));
        manga2 = mangaRepository.save(buildPublicManga("eng-test-manga-beta", "Eng Test Manga Beta", readerA));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        ));
    }

    static User buildUser(String email, String username, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash("$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC");
        u.setPresentationMessage("test");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setQuotaGb(BigDecimal.ONE);
        u.setTotpEnabled(false);
        return u;
    }

    static Manga buildPublicManga(String slug, String title, User owner) {
        Manga m = new Manga();
        m.setSlug(slug);
        m.setTitle(title);
        m.setFormat(MangaFormat.MANGA);
        m.setStatusOrigin(MangaStatusOrigin.ONGOING);
        m.setStatusSite(MangaStatusSite.INCOMPLETE);
        m.setPublic(true);
        m.setOwner(owner);
        return m;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Rating — POST /mangas/{id}/rating
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void rating_POST_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rating_POST_validScore_returns201_withScoreAndRatingCountOne() throws Exception {
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4}")
                        .with(auth(readerA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(4))
                .andExpect(jsonPath("$.ratingCount").value(1));
    }

    @Test
    void rating_POST_scoreZero_returns400() throws Exception {
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":0}")
                        .with(auth(readerA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rating_POST_scoreSix_returns400() throws Exception {
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":6}")
                        .with(auth(readerA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rating_POST_twoUsers_ratingCountTwoAndAvgRatingRecalculated() throws Exception {
        // readerA vota 4
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4}")
                        .with(auth(readerA)))
                .andExpect(status().isCreated());

        // readerB vota 2 → avg (4+2)/2 = 3.0, count=2
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":2}")
                        .with(auth(readerB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ratingCount").value(2))
                .andExpect(jsonPath("$.avgRating").value(3.0));
    }

    @Test
    void rating_POST_duplicate_returns409() throws Exception {
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4}")
                        .with(auth(readerA)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5}")
                        .with(auth(readerA)))
                .andExpect(status().isConflict());
    }

    @Test
    void rating_POST_nonexistentManga_returns404() throws Exception {
        mockMvc.perform(post("/mangas/{id}/rating", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":3}")
                        .with(auth(readerA)))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Rating — PUT /mangas/{id}/rating
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void rating_PUT_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rating_PUT_updatesScore_andRecalculatesAvg() throws Exception {
        // setup: A=4, B=2
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"score\":4}").with(auth(readerA)));
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"score\":2}").with(auth(readerB)));

        // A atualiza para 5 → (5+2)/2 = 3.5
        mockMvc.perform(put("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5}")
                        .with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(5))
                .andExpect(jsonPath("$.avgRating").value(3.5));
    }

    @Test
    void rating_PUT_noExistingRating_returns404() throws Exception {
        mockMvc.perform(put("/mangas/{id}/rating", manga2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":3}")
                        .with(auth(readerA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rating_PUT_invalidScore_returns400() throws Exception {
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"score\":4}").with(auth(readerA)));

        mockMvc.perform(put("/mangas/{id}/rating", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":0}")
                        .with(auth(readerA)))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Rating — DELETE /mangas/{id}/rating
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void rating_DELETE_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/mangas/{id}/rating", manga1.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rating_DELETE_removesRatingAndRecalculatesAvg() throws Exception {
        // setup: A=4, B=2
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"score\":4}").with(auth(readerA)));
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"score\":2}").with(auth(readerB)));

        // A deleta → só B permanece com score=2
        mockMvc.perform(delete("/mangas/{id}/rating", manga1.getId()).with(auth(readerA)))
                .andExpect(status().isNoContent());

        // GET de B deve refletir: ratingCount=1, avgRating=2.0
        mockMvc.perform(get("/mangas/{id}/rating", manga1.getId()).with(auth(readerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingCount").value(1))
                .andExpect(jsonPath("$.avgRating").value(2.0));
    }

    @Test
    void rating_DELETE_nonexistentRating_returns404() throws Exception {
        mockMvc.perform(delete("/mangas/{id}/rating", manga1.getId()).with(auth(readerA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rating_DELETE_lastRating_setsAvgAndRatingCountToZero() throws Exception {
        mockMvc.perform(post("/mangas/{id}/rating", manga1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"score\":2}").with(auth(readerB)));

        mockMvc.perform(delete("/mangas/{id}/rating", manga1.getId()).with(auth(readerB)))
                .andExpect(status().isNoContent());

        // O recálculo zerando deve persistir no manga
        Manga refreshed = mangaRepository.findById(manga1.getId()).orElseThrow();
        assertThat(refreshed.getRatingCount()).isZero();
        assertThat(refreshed.getAvgRating()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ReadingList — GET /reading-list
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readingList_GET_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/reading-list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readingList_GET_emptyList_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/reading-list").with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void readingList_GET_withItems_returnsCorrectCountAndFields() throws Exception {
        // A adiciona 2 itens, B adiciona 1 (deve ser isolado)
        mockMvc.perform(put("/reading-list/{mangaId}", manga1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READING\"}").with(auth(readerA)));
        mockMvc.perform(put("/reading-list/{mangaId}", manga2.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}").with(auth(readerA)));
        mockMvc.perform(put("/reading-list/{mangaId}", manga1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DROPPED\"}").with(auth(readerB)));

        mockMvc.perform(get("/reading-list").with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].mangaId").exists())
                .andExpect(jsonPath("$[0].mangaTitle").exists())
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].updatedAt").exists());

        // isolamento: B tem apenas 1 item
        mockMvc.perform(get("/reading-list").with(auth(readerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ReadingList — PUT /reading-list/{mangaId}
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readingList_PUT_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/reading-list/{mangaId}", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WANT_TO_READ\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readingList_PUT_insert_returnsCorrectStatusAndMangaId() throws Exception {
        mockMvc.perform(put("/reading-list/{mangaId}", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WANT_TO_READ\"}")
                        .with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WANT_TO_READ"))
                .andExpect(jsonPath("$.mangaId").value(manga1.getId().toString()));
    }

    @Test
    void readingList_PUT_upsert_updatesStatus() throws Exception {
        mockMvc.perform(put("/reading-list/{mangaId}", manga1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WANT_TO_READ\"}").with(auth(readerA)));

        mockMvc.perform(put("/reading-list/{mangaId}", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READING\"}")
                        .with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READING"));
    }

    @Test
    void readingList_PUT_invalidStatus_returns400() throws Exception {
        mockMvc.perform(put("/reading-list/{mangaId}", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVALID_STATUS\"}")
                        .with(auth(readerA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readingList_PUT_nullStatus_returns400() throws Exception {
        mockMvc.perform(put("/reading-list/{mangaId}", manga1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(auth(readerA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readingList_PUT_nonexistentManga_returns404() throws Exception {
        mockMvc.perform(put("/reading-list/{mangaId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READING\"}")
                        .with(auth(readerA)))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ReadingList — DELETE /reading-list/{mangaId}
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readingList_DELETE_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/reading-list/{mangaId}", manga1.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readingList_DELETE_existingItem_returns204_andListShrinks() throws Exception {
        mockMvc.perform(put("/reading-list/{mangaId}", manga1.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READING\"}").with(auth(readerA)));
        mockMvc.perform(put("/reading-list/{mangaId}", manga2.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}").with(auth(readerA)));

        mockMvc.perform(delete("/reading-list/{mangaId}", manga2.getId()).with(auth(readerA)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/reading-list").with(auth(readerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void readingList_DELETE_alreadyRemoved_returns404() throws Exception {
        mockMvc.perform(put("/reading-list/{mangaId}", manga2.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}").with(auth(readerA)));
        mockMvc.perform(delete("/reading-list/{mangaId}", manga2.getId()).with(auth(readerA)));

        mockMvc.perform(delete("/reading-list/{mangaId}", manga2.getId()).with(auth(readerA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void readingList_DELETE_nonexistentManga_returns404() throws Exception {
        mockMvc.perform(delete("/reading-list/{mangaId}", UUID.randomUUID()).with(auth(readerA)))
                .andExpect(status().isNotFound());
    }
}
