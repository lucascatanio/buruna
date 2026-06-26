package com.buruna.manga;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.domain.MangaStatusSite;
import com.buruna.manga.domain.Tag;
import com.buruna.manga.domain.TagCategory;
import com.buruna.manga.persistence.TagCategoryRepository;
import com.buruna.manga.persistence.TagRepository;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.storage.StorageClient;
import com.buruna.identity.domain.Email;
import com.buruna.identity.domain.Quota;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.domain.Username;
import com.buruna.identity.persistence.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.web.servlet.ResultActions;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rede de segurança do Epic 4 (manga): reproduz em JUnit os cenários de
 * test-phase3.sh (tags/categorias), test-phase4.sh (catálogo público + volumes)
 * e test-phase5.sh (coleção privada, cota, promote) + fluxo de submissão.
 *
 * Substituição 1:1 conforme ADR-38, com duas adaptações deliberadas ao código ATUAL
 * (fonte da verdade, não o bash):
 *  - GET /tags, /tag-categories e /mangas exigem autenticação (SecurityConfig), então
 *    os GETs "públicos" do bash viram 401 sem token. Os scripts são anteriores a essa
 *    mudança de segurança.
 *  - O upload migrou de multipart (file=@) para 2 fases (upload-url + finalize). A dedup
 *    por hash existe APENAS no fluxo público (FinalizePublicVolumeUseCase); o fluxo privado
 *    (FinalizeVolumeUseCase) deduplica só por número + cota.
 *
 * StorageClient é mockado: generateUploadSignedUrl/generateSignedUrl devolvem URL fake e
 * getFileMetadata devolve, por padrão, um md5 único por objectName (sobrescrito por teste
 * quando o cenário precisa de hash duplicado ou de tamanho que estoura a cota).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class MangaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired MangaRepository mangaRepository;
    @Autowired VolumeRepository volumeRepository;
    @Autowired TagRepository tagRepository;
    @Autowired TagCategoryRepository tagCategoryRepository;
    @MockBean StorageClient storageClient;
    @MockBean EmailService emailService;

    User admin;
    User collab;
    User collabB;
    User reader;
    User readerB;

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
        when(storageClient.generateUploadSignedUrl(anyString(), any(Duration.class))).thenReturn(FAKE_URL);
        // por padrão cada objectName tem um hash único e tamanho pequeno (1 KB)
        when(storageClient.getFileMetadata(anyString()))
                .thenAnswer(inv -> new StorageClient.FileMetadata("md5-" + inv.getArgument(0), 1024L));

        // limpa apenas os dados de churn; mantém os seeds de tags (V12/V13)
        volumeRepository.deleteAllInBatch();
        mangaRepository.deleteAll(); // não-batch: remove as linhas de manga_tags antes do mangá
        userRepository.deleteAllInBatch();

        admin   = userRepository.save(buildUser("admin@manga.test",   "mangaAdmin",   Role.ADMIN));
        collab  = userRepository.save(buildUser("collab@manga.test",  "mangaCollab",  Role.COLLABORATOR));
        collabB = userRepository.save(buildUser("collabB@manga.test", "mangaCollabB", Role.COLLABORATOR));
        reader  = userRepository.save(buildUser("reader@manga.test",  "mangaReader",  Role.READER));
        readerB = userRepository.save(buildUser("readerB@manga.test", "mangaReaderB", Role.READER));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        ));
    }

    static User buildUser(String email, String username, Role role) {
        User u = User.register(Email.of(email), Username.of(username),
                "$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC", "test",
                Quota.of(BigDecimal.ONE));
        u.changeRole(role);
        u.changeStatus(UserStatus.ACTIVE);
        return u;
    }

    static String json(String s) { return s; }

    String createPublicManga(String title, User owner, UUID... tagIds) throws Exception {
        StringBuilder tags = new StringBuilder();
        if (tagIds.length > 0) {
            tags.append(",\"tagIds\":[");
            for (int i = 0; i < tagIds.length; i++) {
                if (i > 0) tags.append(",");
                tags.append("\"").append(tagIds[i]).append("\"");
            }
            tags.append("]");
        }
        String body = "{\"title\":\"" + title + "\",\"format\":\"MANGA\","
                + "\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"" + tags + "}";
        String response = mockMvc.perform(post("/mangas")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(auth(owner)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    String createPrivateManga(String title, User owner) throws Exception {
        String response = mockMvc.perform(post("/my/mangas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}").with(auth(owner)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    /** Pede a upload-url e devolve o objectName gerado pelo servidor. */
    String requestUploadUrl(String base, String mangaId, int volumeNumber, User u) throws Exception {
        String response = mockMvc.perform(post(base + "/{id}/volumes/upload-url", mangaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volumeNumber\":" + volumeNumber + "}").with(auth(u)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.objectName");
    }

    ResultActions finalizeVolume(String base, String mangaId, String objectName, int volumeNumber, User u) throws Exception {
        return mockMvc.perform(post(base + "/{id}/volumes/finalize", mangaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectName\":\"" + objectName + "\",\"volumeNumber\":" + volumeNumber + "}")
                .with(auth(u)));
    }

    /** Sobe um volume completo (upload-url + finalize) no fluxo dado e devolve o objectName. */
    String uploadVolume(String base, String mangaId, int volumeNumber, User u) throws Exception {
        String objectName = requestUploadUrl(base, mangaId, volumeNumber, u);
        finalizeVolume(base, mangaId, objectName, volumeNumber, u).andExpect(status().isCreated());
        return objectName;
    }

    UUID seedTag(String slug) {
        TagCategory category = tagCategoryRepository.save(new TagCategory("ITestCat-" + UUID.randomUUID()));
        Tag tag = new Tag("ITest " + slug, slug + "-" + UUID.randomUUID(), category);
        return tagRepository.save(tag).getId();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  1. Tags / TagCategories  (test-phase3.sh)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Tags {

        @Test
        void getTags_withoutToken_returns401() throws Exception {
            mockMvc.perform(get("/tags")).andExpect(status().isUnauthorized());
        }

        @Test
        void getTagCategories_withoutToken_returns401() throws Exception {
            mockMvc.perform(get("/tag-categories")).andExpect(status().isUnauthorized());
        }

        @Test
        void getTags_authenticated_returns200() throws Exception {
            mockMvc.perform(get("/tags").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        void getTagCategories_seededByFlyway_returns200WithData() throws Exception {
            mockMvc.perform(get("/tag-categories").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
        }

        @Test
        void getTags_seededByFlyway_returns200WithData() throws Exception {
            mockMvc.perform(get("/tags").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)));
        }

        @Test
        void postTagCategory_withoutToken_returns401() throws Exception {
            mockMvc.perform(post("/tag-categories")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void postTagCategory_asReader_returns403() throws Exception {
            mockMvc.perform(post("/tag-categories")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}")
                            .with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void postTagCategory_asAdmin_returns201() throws Exception {
            mockMvc.perform(post("/tag-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ITestCat A\"}").with(auth(admin)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.name").value("ITestCat A"));
        }

        @Test
        void postTagCategory_duplicateName_returns409() throws Exception {
            mockMvc.perform(post("/tag-categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"ITestCat Dup\"}").with(auth(admin)))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/tag-categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"ITestCat Dup\"}").with(auth(admin)))
                    .andExpect(status().isConflict());
        }

        @Test
        void postTagCategory_emptyName_returns400() throws Exception {
            mockMvc.perform(post("/tag-categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\"}").with(auth(admin)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void postTag_asAdmin_returns201() throws Exception {
            String catResp = mockMvc.perform(post("/tag-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ITestCat T\"}").with(auth(admin)))
                    .andReturn().getResponse().getContentAsString();
            String categoryId = JsonPath.read(catResp, "$.id");

            mockMvc.perform(post("/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ITag\",\"slug\":\"itag-1\",\"categoryId\":\"" + categoryId + "\"}")
                            .with(auth(admin)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists());
        }

        @Test
        void postTag_duplicateSlug_returns409() throws Exception {
            String catResp = mockMvc.perform(post("/tag-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ITestCat T2\"}").with(auth(admin)))
                    .andReturn().getResponse().getContentAsString();
            String categoryId = JsonPath.read(catResp, "$.id");

            mockMvc.perform(post("/tags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"ITag\",\"slug\":\"itag-dup\",\"categoryId\":\"" + categoryId + "\"}")
                    .with(auth(admin))).andExpect(status().isCreated());
            mockMvc.perform(post("/tags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"ITag 2\",\"slug\":\"itag-dup\",\"categoryId\":\"" + categoryId + "\"}")
                    .with(auth(admin))).andExpect(status().isConflict());
        }

        @Test
        void postTag_nonexistentCategory_returns404() throws Exception {
            mockMvc.perform(post("/tags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"X\",\"slug\":\"itag-nocat\",\"categoryId\":\""
                            + UUID.randomUUID() + "\"}").with(auth(admin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void postTag_invalidPayload_returns400() throws Exception {
            mockMvc.perform(post("/tags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\",\"slug\":\"\",\"categoryId\":null}").with(auth(admin)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void putTag_updatesName() throws Exception {
            String catResp = mockMvc.perform(post("/tag-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ITestCat Upd\"}").with(auth(admin)))
                    .andReturn().getResponse().getContentAsString();
            String categoryId = JsonPath.read(catResp, "$.id");
            String tagResp = mockMvc.perform(post("/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ITag\",\"slug\":\"itag-upd\",\"categoryId\":\"" + categoryId + "\"}")
                            .with(auth(admin)))
                    .andReturn().getResponse().getContentAsString();
            String tagId = JsonPath.read(tagResp, "$.id");

            mockMvc.perform(put("/tags/{id}", tagId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ITag Editada\",\"slug\":\"itag-upd-2\",\"categoryId\":\"" + categoryId + "\"}")
                            .with(auth(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("ITag Editada"));
        }

        @Test
        void deleteTag_softDeletes_thenSecondDeleteReturns404() throws Exception {
            String catResp = mockMvc.perform(post("/tag-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ITestCat Del\"}").with(auth(admin)))
                    .andReturn().getResponse().getContentAsString();
            String categoryId = JsonPath.read(catResp, "$.id");
            String tagResp = mockMvc.perform(post("/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ITag\",\"slug\":\"itag-del\",\"categoryId\":\"" + categoryId + "\"}")
                            .with(auth(admin)))
                    .andReturn().getResponse().getContentAsString();
            String tagId = JsonPath.read(tagResp, "$.id");

            mockMvc.perform(delete("/tags/{id}", tagId).with(auth(admin)))
                    .andExpect(status().isNoContent());

            // some da listagem ativa
            mockMvc.perform(get("/tags").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id=='" + tagId + "')]").doesNotExist());

            // segundo delete → 404
            mockMvc.perform(delete("/tags/{id}", tagId).with(auth(admin)))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  2. Catálogo público — /mangas  (test-phase4.sh)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class PublicCatalog {

        @Test
        void getMangas_withoutToken_returns401() throws Exception {
            mockMvc.perform(get("/mangas")).andExpect(status().isUnauthorized());
        }

        @Test
        void getMangas_authenticated_returnsPaginated() throws Exception {
            mockMvc.perform(get("/mangas").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        void postManga_withoutToken_returns401() throws Exception {
            mockMvc.perform(post("/mangas").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"X\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void postManga_asReader_returns403() throws Exception {
            mockMvc.perform(post("/mangas").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"X\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void postManga_emptyTitle_returns400() throws Exception {
            mockMvc.perform(post("/mangas").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collab)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void postManga_asCollaborator_returns201WithSlug() throws Exception {
            mockMvc.perform(post("/mangas").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Public One\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collab)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.slug").value("itest-public-one"))
                    .andExpect(jsonPath("$.isPublic").value(true));
        }

        @Test
        void postManga_duplicateTitle_returns409() throws Exception {
            createPublicManga("ITest Dup Title", collab);
            mockMvc.perform(post("/mangas").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Dup Title\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collab)))
                    .andExpect(status().isConflict());
        }

        @Test
        void getMangas_filterByTitle_returnsMatch() throws Exception {
            createPublicManga("ITest Searchable Alpha", collab);
            mockMvc.perform(get("/mangas").param("title", "Searchable").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        }

        @Test
        void getMangas_filterByTagIds_appliesAndSemantics() throws Exception {
            UUID t1 = seedTag("itag-and-1");
            UUID t2 = seedTag("itag-and-2");
            createPublicManga("ITest Both Tags", collab, t1, t2);
            createPublicManga("ITest One Tag", collab, t1);

            // só t1 → ambos
            mockMvc.perform(get("/mangas").param("tagIds", t1.toString()).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));

            // t1 AND t2 → apenas o que tem as duas
            mockMvc.perform(get("/mangas")
                            .param("tagIds", t1.toString())
                            .param("tagIds", t2.toString())
                            .with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("ITest Both Tags"));
        }

        @Test
        void getMangaBySlug_returns200() throws Exception {
            createPublicManga("ITest By Slug", collab);
            mockMvc.perform(get("/mangas/{slug}", "itest-by-slug").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("itest-by-slug"))
                    .andExpect(jsonPath("$.volumes").isArray());
        }

        @Test
        void getMangaById_returns200() throws Exception {
            String id = createPublicManga("ITest By Id", collab);
            mockMvc.perform(get("/mangas/{id}", id).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id));
        }

        @Test
        void getManga_nonexistentSlug_returns404() throws Exception {
            mockMvc.perform(get("/mangas/{slug}", "no-such-slug-xyz").with(auth(reader)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void getManga_withoutToken_returns401() throws Exception {
            String id = createPublicManga("ITest Protected Detail", collab);
            mockMvc.perform(get("/mangas/{id}", id)).andExpect(status().isUnauthorized());
        }

        @Test
        void putManga_asOwner_returns200() throws Exception {
            String id = createPublicManga("ITest Editable", collab);
            mockMvc.perform(put("/mangas/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Edited\",\"format\":\"MANGA\",\"statusOrigin\":\"HIATUS\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collab)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("ITest Edited"));
        }

        @Test
        void putManga_asAdminNonOwner_returns200() throws Exception {
            String id = createPublicManga("ITest Admin Edit", collab);
            mockMvc.perform(put("/mangas/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Admin Edited\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(admin)))
                    .andExpect(status().isOk());
        }

        @Test
        void putManga_asNonOwnerCollaborator_returns403() throws Exception {
            String id = createPublicManga("ITest Foreign Edit", collab);
            mockMvc.perform(put("/mangas/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Hijack\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collabB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void putManga_asReader_returns403() throws Exception {
            String id = createPublicManga("ITest Reader Edit", collab);
            mockMvc.perform(put("/mangas/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Hijack\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void putManga_nonexistent_returns404() throws Exception {
            mockMvc.perform(put("/mangas/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Ghost\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(admin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteManga_asReader_returns403() throws Exception {
            String id = createPublicManga("ITest Del Reader", collab);
            mockMvc.perform(delete("/mangas/{id}", id).with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void deleteManga_asNonOwnerCollaborator_returns403() throws Exception {
            String id = createPublicManga("ITest Del Foreign", collab);
            mockMvc.perform(delete("/mangas/{id}", id).with(auth(collabB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void deleteManga_asOwner_returns204AndDisappears() throws Exception {
            String id = createPublicManga("ITest Del Owner", collab);
            mockMvc.perform(delete("/mangas/{id}", id).with(auth(collab)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/mangas/{id}", id).with(auth(reader)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteManga_asAdminNonOwner_returns204() throws Exception {
            String id = createPublicManga("ITest Del Admin", collab);
            mockMvc.perform(delete("/mangas/{id}", id).with(auth(admin)))
                    .andExpect(status().isNoContent());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  3. Volumes públicos — upload 2 fases  (test-phase4.sh)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class PublicVolumes {

        @Test
        void listVolumes_withoutToken_returns401() throws Exception {
            String id = createPublicManga("ITest Vol List Auth", collab);
            mockMvc.perform(get("/mangas/{id}/volumes", id)).andExpect(status().isUnauthorized());
        }

        @Test
        void listVolumes_nonexistentManga_returns404() throws Exception {
            mockMvc.perform(get("/mangas/{id}/volumes", UUID.randomUUID()).with(auth(reader)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void uploadUrl_withoutToken_returns401() throws Exception {
            String id = createPublicManga("ITest Vol NoToken", collab);
            mockMvc.perform(post("/mangas/{id}/volumes/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void uploadUrl_asReader_returns403() throws Exception {
            String id = createPublicManga("ITest Vol Reader", collab);
            mockMvc.perform(post("/mangas/{id}/volumes/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void uploadUrl_nonexistentManga_returns404() throws Exception {
            mockMvc.perform(post("/mangas/{id}/volumes/upload-url", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(collab)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void uploadAndFinalize_asOwner_returns201AndListsVolume() throws Exception {
            String id = createPublicManga("ITest Vol Happy", collab);
            uploadVolume("/mangas", id, 1, collab);

            mockMvc.perform(get("/mangas/{id}/volumes", id).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].volumeNumber").value(1));
        }

        @Test
        void uploadUrl_duplicateVolumeNumber_returns409() throws Exception {
            String id = createPublicManga("ITest Vol DupNum", collab);
            uploadVolume("/mangas", id, 1, collab);

            mockMvc.perform(post("/mangas/{id}/volumes/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(collab)))
                    .andExpect(status().isConflict());
        }

        @Test
        void finalize_duplicateFileHash_returns409() throws Exception {
            String id = createPublicManga("ITest Vol DupHash", collab);
            String obj1 = uploadVolume("/mangas", id, 1, collab); // hash = md5-obj1

            String obj2 = requestUploadUrl("/mangas", id, 2, collab);
            // força o mesmo hash do volume 1
            when(storageClient.getFileMetadata(eq(obj2)))
                    .thenReturn(new StorageClient.FileMetadata("md5-" + obj1, 1024L));

            finalizeVolume("/mangas", id, obj2, 2, collab)
                    .andExpect(status().isConflict());
        }

        @Test
        void deleteVolume_asReader_returns403() throws Exception {
            String id = createPublicManga("ITest Vol Del Reader", collab);
            uploadVolume("/mangas", id, 1, collab);
            UUID volId = volumeRepository.findByMangaId(UUID.fromString(id)).get(0).getId();

            mockMvc.perform(delete("/mangas/{id}/volumes/{vid}", id, volId).with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void deleteVolume_asOwner_returns204ThenSecondReturns404() throws Exception {
            String id = createPublicManga("ITest Vol Del Owner", collab);
            uploadVolume("/mangas", id, 1, collab);
            UUID volId = volumeRepository.findByMangaId(UUID.fromString(id)).get(0).getId();

            mockMvc.perform(delete("/mangas/{id}/volumes/{vid}", id, volId).with(auth(collab)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/mangas/{id}/volumes/{vid}", id, volId).with(auth(collab)))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  4. Coleção privada — /my/mangas  (test-phase5.sh)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class PrivateCollection {

        @Test
        void listMine_withoutToken_returns401() throws Exception {
            mockMvc.perform(get("/my/mangas")).andExpect(status().isUnauthorized());
        }

        @Test
        void listMine_authenticated_returnsPaginated() throws Exception {
            mockMvc.perform(get("/my/mangas").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        void getQuota_withoutToken_returns401() throws Exception {
            mockMvc.perform(get("/my/mangas/quota")).andExpect(status().isUnauthorized());
        }

        @Test
        void getQuota_returnsQuotaBytes() throws Exception {
            mockMvc.perform(get("/my/mangas/quota").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quotaBytes").value(org.hamcrest.Matchers.greaterThan(0)));
        }

        @Test
        void create_withoutToken_returns401() throws Exception {
            mockMvc.perform(post("/my/mangas").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"X\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void create_asReader_returns201() throws Exception {
            String id = createPrivateManga("ITest Private One", reader);
            org.assertj.core.api.Assertions.assertThat(id).isNotBlank();
        }

        @Test
        void create_notVisibleInPublicCatalog() throws Exception {
            createPrivateManga("ITest Private Hidden", reader);
            mockMvc.perform(get("/mangas").param("title", "ITest Private Hidden").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void list_isolatedByOwner() throws Exception {
            createPrivateManga("ITest Owner A Manga", reader);

            mockMvc.perform(get("/my/mangas").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
            // outro usuário não enxerga
            mockMvc.perform(get("/my/mangas").with(auth(readerB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void findById_byNonOwner_returns403() throws Exception {
            String id = createPrivateManga("ITest Private Foreign", reader);
            mockMvc.perform(get("/my/mangas/{id}", id).with(auth(readerB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void uploadVolume_happyPath_listsVolume() throws Exception {
            String id = createPrivateManga("ITest Private Upload", reader);
            String response = mockMvc.perform(post("/my/mangas/{id}/volumes/upload-url", id)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(reader)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String objectName = JsonPath.read(response, "$.objectName");

            mockMvc.perform(post("/my/mangas/{id}/volumes/finalize", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"objectName\":\"" + objectName + "\",\"volumeNumber\":1}").with(auth(reader)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.volumes.length()").value(1));
        }

        @Test
        void uploadVolume_duplicateNumber_returns409() throws Exception {
            String id = createPrivateManga("ITest Private DupNum", reader);
            uploadVolume("/my/mangas", id, 1, reader);

            // a dedup por número é feita já na fase upload-url (GenerateVolumeUploadUrlUseCase)
            mockMvc.perform(post("/my/mangas/{id}/volumes/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(reader)))
                    .andExpect(status().isConflict());
        }

        @Test
        void uploadVolume_byNonOwner_returns403() throws Exception {
            String id = createPrivateManga("ITest Private Vol Foreign", reader);
            mockMvc.perform(post("/my/mangas/{id}/volumes/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(readerB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void finalizeVolume_exceedingQuota_returns422() throws Exception {
            String id = createPrivateManga("ITest Private Quota", reader);
            String objectName = requestUploadUrl("/my/mangas", id, 1, reader);
            // 2 GB > cota de 1 GB
            when(storageClient.getFileMetadata(eq(objectName)))
                    .thenReturn(new StorageClient.FileMetadata("md5-quota", 2L * 1024 * 1024 * 1024));

            // CORRIGIDO no [4.4]: a cota estourada virou InsufficientStorageQuotaException de
            // DOMÍNIO pura (DomainErrorType.UNPROCESSABLE), traduzida pelo GlobalExceptionHandler
            // para 422. Antes, o @ResponseStatus(422) da exceção legada era engolido pelo
            // @ExceptionHandler(Exception.class), devolvendo 500 (bug latente).
            finalizeVolume("/my/mangas", id, objectName, 1, reader)
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void update_asOwner_returns200() throws Exception {
            String id = createPrivateManga("ITest Private Edit", reader);
            mockMvc.perform(put("/my/mangas/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Private Edited\",\"synopsis\":\"nova\"}").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("ITest Private Edited"));
        }

        @Test
        void update_byNonOwner_returns403() throws Exception {
            String id = createPrivateManga("ITest Private Edit Foreign", reader);
            mockMvc.perform(put("/my/mangas/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Hijack\",\"synopsis\":\"\"}").with(auth(readerB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void update_nonexistent_returns404() throws Exception {
            mockMvc.perform(put("/my/mangas/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Ghost\",\"synopsis\":\"\"}").with(auth(reader)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteVolume_byNonOwner_returns403() throws Exception {
            String id = createPrivateManga("ITest Private DelVol Foreign", reader);
            uploadVolume("/my/mangas", id, 1, reader);
            UUID volId = volumeRepository.findByMangaId(UUID.fromString(id)).get(0).getId();

            mockMvc.perform(delete("/my/mangas/{id}/volumes/{vid}", id, volId).with(auth(readerB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void deleteVolume_asOwner_returns200ThenSecondReturns404() throws Exception {
            String id = createPrivateManga("ITest Private DelVol", reader);
            uploadVolume("/my/mangas", id, 1, reader);
            uploadVolume("/my/mangas", id, 2, reader);
            UUID volId = volumeRepository.findByMangaId(UUID.fromString(id)).stream()
                    .filter(v -> v.getVolumeNumber() == 2).findFirst().orElseThrow().getId();

            mockMvc.perform(delete("/my/mangas/{id}/volumes/{vid}", id, volId).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.volumes.length()").value(1));
            mockMvc.perform(delete("/my/mangas/{id}/volumes/{vid}", id, volId).with(auth(reader)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void delete_byNonOwner_returns403() throws Exception {
            String id = createPrivateManga("ITest Private Del Foreign", reader);
            mockMvc.perform(delete("/my/mangas/{id}", id).with(auth(readerB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void delete_asOwner_returns204ThenSecondReturns404() throws Exception {
            String id = createPrivateManga("ITest Private Del", reader);
            mockMvc.perform(delete("/my/mangas/{id}", id).with(auth(reader)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/my/mangas/{id}", id).with(auth(reader)))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  5. Promote  (test-phase5.sh §7)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Promote {

        @Test
        void promote_asReader_returns403() throws Exception {
            String id = createPrivateManga("ITest Promote Reader", reader);
            mockMvc.perform(post("/my/mangas/{id}/promote", id).with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void promote_asCollaborator_movesToPublicCatalog() throws Exception {
            String id = createPrivateManga("ITest Promote Collab", collab);

            mockMvc.perform(post("/my/mangas/{id}/promote", id).with(auth(collab)))
                    .andExpect(status().isOk());

            // some da coleção privada
            mockMvc.perform(get("/my/mangas").with(auth(collab)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
            // aparece na biblioteca pública
            mockMvc.perform(get("/mangas").param("title", "ITest Promote Collab").with(auth(collab)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void promote_titleAlreadyPublic_returns409() throws Exception {
            createPublicManga("ITest Promote Conflict", collab);
            String privateId = createPrivateManga("ITest Promote Conflict", collab);

            mockMvc.perform(post("/my/mangas/{id}/promote", privateId).with(auth(collab)))
                    .andExpect(status().isConflict());
        }

        @Test
        void promote_volumeHashAlreadyPublic_returns409() throws Exception {
            // mangá público com volume de hash conhecido
            String publicId = createPublicManga("ITest Hash Public", collab);
            String publicObj = uploadVolume("/mangas", publicId, 1, collab); // hash = md5-publicObj

            // mangá privado cujo volume tem o MESMO hash
            String privateId = createPrivateManga("ITest Hash Private", collab);
            String privObj = requestUploadUrl("/my/mangas", privateId, 1, collab);
            when(storageClient.getFileMetadata(eq(privObj)))
                    .thenReturn(new StorageClient.FileMetadata("md5-" + publicObj, 1024L));
            finalizeVolume("/my/mangas", privateId, privObj, 1, collab).andExpect(status().isCreated());

            mockMvc.perform(post("/my/mangas/{id}/promote", privateId).with(auth(collab)))
                    .andExpect(status().isConflict());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  6. Submissão → aprovação / rejeição  (fluxo de moderação)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Submission {

        @Test
        void submit_asOwner_setsPending() throws Exception {
            String id = createPrivateManga("ITest Submit One", reader);
            mockMvc.perform(post("/my/mangas/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.submissionStatus").value("PENDING"));
        }

        @Test
        void submit_whenAlreadyPending_returns409() throws Exception {
            String id = createPrivateManga("ITest Submit Twice", reader);
            mockMvc.perform(post("/my/mangas/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/my/mangas/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isConflict());
        }

        @Test
        void listPending_asReader_returns403() throws Exception {
            mockMvc.perform(get("/admin/submissions").with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void listPending_asAdmin_showsSubmitted() throws Exception {
            String id = createPrivateManga("ITest Submit Listed", reader);
            mockMvc.perform(post("/my/mangas/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/admin/submissions").with(auth(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        }

        @Test
        void approve_makesPublic() throws Exception {
            String id = createPrivateManga("ITest Submit Approve", reader);
            mockMvc.perform(post("/my/mangas/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/admin/submissions/{id}/approve", id).with(auth(admin)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/mangas").param("title", "ITest Submit Approve").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void approve_asReader_returns403() throws Exception {
            String id = createPrivateManga("ITest Submit Approve Forbidden", reader);
            mockMvc.perform(post("/my/mangas/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/admin/submissions/{id}/approve", id).with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void reject_setsRejectedWithReason() throws Exception {
            String id = createPrivateManga("ITest Submit Reject", reader);
            mockMvc.perform(post("/my/mangas/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/admin/submissions/{id}/reject", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rejectionReason\":\"qualidade insuficiente\"}").with(auth(admin)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/my/mangas/{id}", id).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.submissionStatus").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReason").value("qualidade insuficiente"));
        }

        @Test
        void approve_whenNotPending_returns400() throws Exception {
            String id = createPrivateManga("ITest Submit NotPending", reader);
            mockMvc.perform(post("/admin/submissions/{id}/approve", id).with(auth(admin)))
                    .andExpect(status().isBadRequest());
        }
    }
}
