package com.buruna.work;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkFormat;
import com.buruna.work.domain.WorkStatusOrigin;
import com.buruna.work.domain.WorkStatusSite;
import com.buruna.work.domain.Tag;
import com.buruna.work.domain.TagCategory;
import com.buruna.work.domain.Volume;
import com.buruna.work.persistence.TagCategoryRepository;
import com.buruna.work.persistence.TagRepository;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.work.persistence.VolumeRepository;
import com.buruna.shared.exception.StorageObjectNotFoundException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rede de segurança do Epic 4 (work): reproduz em JUnit os cenários de
 * test-phase3.sh (tags/categorias), test-phase4.sh (catálogo público + volumes)
 * e test-phase5.sh (coleção privada, cota, promote) + fluxo de submissão.
 *
 * Substituição 1:1 conforme ADR-38, com duas adaptações deliberadas ao código ATUAL
 * (fonte da verdade, não o bash):
 *  - GET /tags, /tag-categories e /works exigem autenticação (SecurityConfig), então
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
class WorkIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @Autowired VolumeRepository volumeRepository;
    @Autowired TagRepository tagRepository;
    @Autowired TagCategoryRepository tagCategoryRepository;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @MockitoBean StorageClient storageClient;
    @MockitoBean EmailService emailService;

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
        when(storageClient.generateUploadSignedUrl(anyString(), anyString(), any(Duration.class)))
                .thenReturn(new StorageClient.SignedUpload(FAKE_URL, java.util.Map.of()));
        // por padrão cada objectName tem um hash único e tamanho pequeno (1 KB). O hash é
        // derivado do objectName (não copiado dele): objectName pendente agora carrega o
        // workId no caminho (ADR-40) e passaria de 64 chars, o limite da coluna file_hash.
        when(storageClient.getFileMetadata(anyString()))
                .thenAnswer(inv -> new StorageClient.FileMetadata(fakeMd5(inv.getArgument(0)), 1024L));

        // limpa apenas os dados de churn; mantém os seeds de tags (V12/V13)
        volumeRepository.deleteAllInBatch();
        workRepository.deleteAll(); // não-batch: remove as linhas de work_tags antes do mangá
        userRepository.deleteAllInBatch();

        admin   = userRepository.save(buildUser("admin@work.test",   "workAdmin",   Role.ADMIN));
        collab  = userRepository.save(buildUser("collab@work.test",  "workCollab",  Role.COLLABORATOR));
        collabB = userRepository.save(buildUser("collabB@work.test", "workCollabB", Role.COLLABORATOR));
        reader  = userRepository.save(buildUser("reader@work.test",  "workReader",  Role.READER));
        readerB = userRepository.save(buildUser("readerB@work.test", "workReaderB", Role.READER));
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

    /** Hash falso e curto (coluna file_hash é VARCHAR(64)) derivado — não copiado — do objectName. */
    static String fakeMd5(String objectName) {
        return "md5-" + Integer.toHexString(objectName.hashCode());
    }

    String createPublicWork(String title, User owner, UUID... tagIds) throws Exception {
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
        String response = mockMvc.perform(post("/works")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(auth(owner)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    String createPrivateWork(String title, User owner) throws Exception {
        String response = mockMvc.perform(post("/my/works")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}").with(auth(owner)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    /** Pede a upload-url e devolve o objectName gerado pelo servidor. */
    String requestUploadUrl(String base, String workId, int volumeNumber, User u) throws Exception {
        String response = mockMvc.perform(post(base + "/{id}/volumes/upload-url", workId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volumeNumber\":" + volumeNumber + "}").with(auth(u)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.objectName");
    }

    ResultActions finalizeVolume(String base, String workId, String objectName, int volumeNumber, User u) throws Exception {
        return mockMvc.perform(post(base + "/{id}/volumes/finalize", workId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectName\":\"" + objectName + "\",\"volumeNumber\":" + volumeNumber + "}")
                .with(auth(u)));
    }

    /** Sobe um volume completo (upload-url + finalize) no fluxo dado e devolve o objectName. */
    String uploadVolume(String base, String workId, int volumeNumber, User u) throws Exception {
        String objectName = requestUploadUrl(base, workId, volumeNumber, u);
        finalizeVolume(base, workId, objectName, volumeNumber, u).andExpect(status().isCreated());
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
    //  2. Catálogo público — /works  (test-phase4.sh)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class PublicCatalog {

        @Test
        void getWorks_withoutToken_returns401() throws Exception {
            mockMvc.perform(get("/works")).andExpect(status().isUnauthorized());
        }

        @Test
        void getWorks_authenticated_returnsPaginated() throws Exception {
            mockMvc.perform(get("/works").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        void postWork_withoutToken_returns401() throws Exception {
            mockMvc.perform(post("/works").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"X\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void postWork_asReader_returns403() throws Exception {
            mockMvc.perform(post("/works").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"X\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void postWork_emptyTitle_returns400() throws Exception {
            mockMvc.perform(post("/works").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collab)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void postWork_titleOver255Chars_returns400() throws Exception {
            mockMvc.perform(post("/works").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"" + "a".repeat(256) + "\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collab)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("255")));
        }

        @Test
        void postWork_originCountryOver100Chars_returns400() throws Exception {
            mockMvc.perform(post("/works").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Long Country\",\"originCountry\":\"" + "a".repeat(101) + "\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collab)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("100")));
        }

        @Test
        void getWorks_sortByAllowedField_returns200() throws Exception {
            mockMvc.perform(get("/works").param("sort", "avgRating,desc").with(auth(reader)))
                    .andExpect(status().isOk());
        }

        @Test
        void getWorks_sortByModerationField_returns400() throws Exception {
            // campo da entidade fora do DTO: ordenar por ele vazaria a ordem dos dados de moderação
            mockMvc.perform(get("/works").param("sort", "reviewedAt").with(auth(reader)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void postWork_asCollaborator_returns201WithSlug() throws Exception {
            mockMvc.perform(post("/works").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Public One\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collab)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.slug").value("itest-public-one"))
                    .andExpect(jsonPath("$.isPublic").value(true));
        }

        @Test
        void postWork_duplicateTitle_returns409() throws Exception {
            createPublicWork("ITest Dup Title", collab);
            mockMvc.perform(post("/works").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Dup Title\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collab)))
                    .andExpect(status().isConflict());
        }

        @Test
        void getWorks_filterByTitle_returnsMatch() throws Exception {
            createPublicWork("ITest Searchable Alpha", collab);
            mockMvc.perform(get("/works").param("title", "Searchable").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        }

        @Test
        void getWorks_filterByTagIds_appliesAndSemantics() throws Exception {
            UUID t1 = seedTag("itag-and-1");
            UUID t2 = seedTag("itag-and-2");
            createPublicWork("ITest Both Tags", collab, t1, t2);
            createPublicWork("ITest One Tag", collab, t1);

            // só t1 → ambos
            mockMvc.perform(get("/works").param("tagIds", t1.toString()).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));

            // t1 AND t2 → apenas o que tem as duas
            mockMvc.perform(get("/works")
                            .param("tagIds", t1.toString())
                            .param("tagIds", t2.toString())
                            .with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("ITest Both Tags"));
        }

        @Test
        void getWorkBySlug_returns200() throws Exception {
            createPublicWork("ITest By Slug", collab);
            mockMvc.perform(get("/works/{slug}", "itest-by-slug").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("itest-by-slug"))
                    .andExpect(jsonPath("$.volumes").isArray());
        }

        @Test
        void shouldServeLegacyRoute_whenOldFrontendStillCallsMangas() throws Exception {
            // Arrange
            String id = createPublicWork("ITest Rota Antiga", collab);

            // Act
            ResultActions result = mockMvc.perform(get("/mangas/{id}", id).with(auth(reader)));

            // Assert
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id));
        }

        @Test
        void getWorkById_returns200() throws Exception {
            String id = createPublicWork("ITest By Id", collab);
            mockMvc.perform(get("/works/{id}", id).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id));
        }

        @Test
        void getWork_nonexistentSlug_returns404() throws Exception {
            mockMvc.perform(get("/works/{slug}", "no-such-slug-xyz").with(auth(reader)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void getWork_withoutToken_returns401() throws Exception {
            String id = createPublicWork("ITest Protected Detail", collab);
            mockMvc.perform(get("/works/{id}", id)).andExpect(status().isUnauthorized());
        }

        @Test
        void putWork_asOwner_returns200() throws Exception {
            String id = createPublicWork("ITest Editable", collab);
            mockMvc.perform(put("/works/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Edited\",\"format\":\"MANGA\",\"statusOrigin\":\"HIATUS\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collab)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("ITest Edited"));
        }

        @Test
        void putWork_asAdminNonOwner_returns200() throws Exception {
            String id = createPublicWork("ITest Admin Edit", collab);
            mockMvc.perform(put("/works/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Admin Edited\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(admin)))
                    .andExpect(status().isOk());
        }

        @Test
        void putWork_asNonOwnerCollaborator_returns403() throws Exception {
            String id = createPublicWork("ITest Foreign Edit", collab);
            mockMvc.perform(put("/works/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Hijack\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(collabB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void putWork_asReader_returns403() throws Exception {
            String id = createPublicWork("ITest Reader Edit", collab);
            mockMvc.perform(put("/works/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Hijack\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void putWork_nonexistent_returns404() throws Exception {
            mockMvc.perform(put("/works/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Ghost\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                    .with(auth(admin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteWork_asReader_returns403() throws Exception {
            String id = createPublicWork("ITest Del Reader", collab);
            mockMvc.perform(delete("/works/{id}", id).with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void deleteWork_asNonOwnerCollaborator_returns403() throws Exception {
            String id = createPublicWork("ITest Del Foreign", collab);
            mockMvc.perform(delete("/works/{id}", id).with(auth(collabB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void deleteWork_asOwner_returns204AndDisappears() throws Exception {
            String id = createPublicWork("ITest Del Owner", collab);
            mockMvc.perform(delete("/works/{id}", id).with(auth(collab)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/works/{id}", id).with(auth(reader)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteWork_asAdminNonOwner_returns204() throws Exception {
            String id = createPublicWork("ITest Del Admin", collab);
            mockMvc.perform(delete("/works/{id}", id).with(auth(admin)))
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
            String id = createPublicWork("ITest Vol List Auth", collab);
            mockMvc.perform(get("/works/{id}/volumes", id)).andExpect(status().isUnauthorized());
        }

        @Test
        void listVolumes_nonexistentWork_returns404() throws Exception {
            mockMvc.perform(get("/works/{id}/volumes", UUID.randomUUID()).with(auth(reader)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void uploadUrl_withoutToken_returns401() throws Exception {
            String id = createPublicWork("ITest Vol NoToken", collab);
            mockMvc.perform(post("/works/{id}/volumes/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void uploadUrl_asReader_returns403() throws Exception {
            String id = createPublicWork("ITest Vol Reader", collab);
            mockMvc.perform(post("/works/{id}/volumes/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void uploadUrl_nonexistentWork_returns404() throws Exception {
            mockMvc.perform(post("/works/{id}/volumes/upload-url", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(collab)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void uploadAndFinalize_asOwner_returns201AndListsVolume() throws Exception {
            String id = createPublicWork("ITest Vol Happy", collab);
            uploadVolume("/works", id, 1, collab);

            mockMvc.perform(get("/works/{id}/volumes", id).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].volumeNumber").value(1));
        }

        @Test
        void uploadUrl_duplicateVolumeNumber_returns409() throws Exception {
            String id = createPublicWork("ITest Vol DupNum", collab);
            uploadVolume("/works", id, 1, collab);

            mockMvc.perform(post("/works/{id}/volumes/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(collab)))
                    .andExpect(status().isConflict());
        }

        @Test
        void finalize_duplicateFileHash_returns409() throws Exception {
            String id = createPublicWork("ITest Vol DupHash", collab);
            String obj1 = uploadVolume("/works", id, 1, collab); // hash = fakeMd5(obj1)

            String obj2 = requestUploadUrl("/works", id, 2, collab);
            // força o mesmo hash do volume 1
            when(storageClient.getFileMetadata(eq(obj2)))
                    .thenReturn(new StorageClient.FileMetadata(fakeMd5(obj1), 1024L));

            finalizeVolume("/works", id, obj2, 2, collab)
                    .andExpect(status().isConflict());
        }

        @Test
        void finalize_sameObjectNameTwice_returns404() throws Exception {
            String id = createPublicWork("ITest Vol Finalize Twice", collab);
            String obj = uploadVolume("/works", id, 1, collab);
            // o primeiro finalize moveu o objeto para fora de pending/
            when(storageClient.getFileMetadata(eq(obj)))
                    .thenThrow(new StorageObjectNotFoundException("Objeto não encontrado no GCS: " + obj));

            finalizeVolume("/works", id, obj, 1, collab)
                    .andExpect(status().isNotFound());
        }

        @Test
        void finalize_objectGoneBeforeMove_returns404AndSavesNoVolume() throws Exception {
            // perdedor de dois finalizes concorrentes: leu o metadado, mas o vencedor moveu antes
            String id = createPublicWork("ITest Vol Finalize Race", collab);
            String obj = requestUploadUrl("/works", id, 1, collab);
            doThrow(new StorageObjectNotFoundException("Objeto não encontrado no GCS: " + obj))
                    .when(storageClient).move(eq(obj), any());

            finalizeVolume("/works", id, obj, 1, collab)
                    .andExpect(status().isNotFound());
            org.assertj.core.api.Assertions.assertThat(volumeRepository.findByWorkId(UUID.fromString(id))).isEmpty();
        }

        @Test
        void deleteVolume_asReader_returns403() throws Exception {
            String id = createPublicWork("ITest Vol Del Reader", collab);
            uploadVolume("/works", id, 1, collab);
            UUID volId = volumeRepository.findByWorkId(UUID.fromString(id)).get(0).getId();

            mockMvc.perform(delete("/works/{id}/volumes/{vid}", id, volId).with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void deleteVolume_asOwner_returns204ThenSecondReturns404() throws Exception {
            String id = createPublicWork("ITest Vol Del Owner", collab);
            uploadVolume("/works", id, 1, collab);
            UUID volId = volumeRepository.findByWorkId(UUID.fromString(id)).get(0).getId();

            mockMvc.perform(delete("/works/{id}/volumes/{vid}", id, volId).with(auth(collab)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/works/{id}/volumes/{vid}", id, volId).with(auth(collab)))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  4. Coleção privada — /my/works  (test-phase5.sh)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class PrivateCollection {

        @Test
        void listMine_withoutToken_returns401() throws Exception {
            mockMvc.perform(get("/my/works")).andExpect(status().isUnauthorized());
        }

        @Test
        void listMine_authenticated_returnsPaginated() throws Exception {
            mockMvc.perform(get("/my/works").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        void getQuota_withoutToken_returns401() throws Exception {
            mockMvc.perform(get("/my/works/quota")).andExpect(status().isUnauthorized());
        }

        @Test
        void getQuota_returnsQuotaBytes() throws Exception {
            mockMvc.perform(get("/my/works/quota").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quotaBytes").value(org.hamcrest.Matchers.greaterThan(0)));
        }

        @Test
        void create_withoutToken_returns401() throws Exception {
            mockMvc.perform(post("/my/works").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"X\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void create_asReader_returns201() throws Exception {
            String id = createPrivateWork("ITest Private One", reader);
            org.assertj.core.api.Assertions.assertThat(id).isNotBlank();
        }

        @Test
        void create_notVisibleInPublicCatalog() throws Exception {
            createPrivateWork("ITest Private Hidden", reader);
            mockMvc.perform(get("/works").param("title", "ITest Private Hidden").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void list_isolatedByOwner() throws Exception {
            createPrivateWork("ITest Owner A Work", reader);

            mockMvc.perform(get("/my/works").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
            // outro usuário não enxerga
            mockMvc.perform(get("/my/works").with(auth(readerB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void findById_byNonOwner_returns403() throws Exception {
            String id = createPrivateWork("ITest Private Foreign", reader);
            mockMvc.perform(get("/my/works/{id}", id).with(auth(readerB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void uploadVolume_happyPath_listsVolume() throws Exception {
            String id = createPrivateWork("ITest Private Upload", reader);
            String response = mockMvc.perform(post("/my/works/{id}/volumes/upload-url", id)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(reader)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String objectName = JsonPath.read(response, "$.objectName");

            mockMvc.perform(post("/my/works/{id}/volumes/finalize", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"objectName\":\"" + objectName + "\",\"volumeNumber\":1}").with(auth(reader)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.volumes.length()").value(1));
        }

        @Test
        void uploadVolume_duplicateNumber_returns409() throws Exception {
            String id = createPrivateWork("ITest Private DupNum", reader);
            uploadVolume("/my/works", id, 1, reader);

            // a dedup por número é feita já na fase upload-url (GenerateVolumeUploadUrlUseCase)
            mockMvc.perform(post("/my/works/{id}/volumes/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(reader)))
                    .andExpect(status().isConflict());
        }

        @Test
        void uploadVolume_byNonOwner_returns403() throws Exception {
            String id = createPrivateWork("ITest Private Vol Foreign", reader);
            mockMvc.perform(post("/my/works/{id}/volumes/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"volumeNumber\":1}").with(auth(readerB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void finalizeVolume_exceedingQuota_returns422() throws Exception {
            String id = createPrivateWork("ITest Private Quota", reader);
            String objectName = requestUploadUrl("/my/works", id, 1, reader);
            // 2 GB > cota de 1 GB
            when(storageClient.getFileMetadata(eq(objectName)))
                    .thenReturn(new StorageClient.FileMetadata("md5-quota", 2L * 1024 * 1024 * 1024));

            // CORRIGIDO no [4.4]: a cota estourada virou InsufficientStorageQuotaException de
            // DOMÍNIO pura (DomainErrorType.UNPROCESSABLE), traduzida pelo GlobalExceptionHandler
            // para 422. Antes, o @ResponseStatus(422) da exceção legada era engolido pelo
            // @ExceptionHandler(Exception.class), devolvendo 500 (bug latente).
            finalizeVolume("/my/works", id, objectName, 1, reader)
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldAcceptOnlyOne_whenTwoConcurrentFinalizesTogetherExceedQuota() throws Exception {
            String idA = createPrivateWork("ITest Private Race A", reader);
            String idB = createPrivateWork("ITest Private Race B", reader);
            String objectA = requestUploadUrl("/my/works", idA, 1, reader);
            String objectB = requestUploadUrl("/my/works", idB, 1, reader);
            // 600 MB cada: um sozinho cabe na cota de 1 GB, os dois juntos não
            long size = 600L * 1024 * 1024;
            when(storageClient.getFileMetadata(eq(objectA)))
                    .thenReturn(new StorageClient.FileMetadata("md5-race-a", size));
            when(storageClient.getFileMetadata(eq(objectB)))
                    .thenReturn(new StorageClient.FileMetadata("md5-race-b", size));
            // o move roda depois da checagem de cota: segurar cada finalize ali até o outro
            // chegar (ou 2 s) garante que, sem serialização, os dois leiam o uso antes de
            // qualquer commit
            CountDownLatch bothPastQuotaCheck = new CountDownLatch(2);
            doAnswer(inv -> {
                bothPastQuotaCheck.countDown();
                bothPastQuotaCheck.await(2, TimeUnit.SECONDS);
                return null;
            }).when(storageClient).move(anyString(), anyString());

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> a = pool.submit(() -> finalizeVolume("/my/works", idA, objectA, 1, reader)
                        .andReturn().getResponse().getStatus());
                Future<Integer> b = pool.submit(() -> finalizeVolume("/my/works", idB, objectB, 1, reader)
                        .andReturn().getResponse().getStatus());

                org.assertj.core.api.Assertions.assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(201, 422);
            } finally {
                pool.shutdownNow();
            }
            org.assertj.core.api.Assertions.assertThat(volumeRepository.sumPrivateFileSizeByOwnerId(reader.getId()))
                    .isEqualTo(size);
        }

        @Test
        void update_asOwner_returns200() throws Exception {
            String id = createPrivateWork("ITest Private Edit", reader);
            mockMvc.perform(put("/my/works/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Private Edited\",\"synopsis\":\"nova\"}").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("ITest Private Edited"));
        }

        @Test
        void update_byNonOwner_returns403() throws Exception {
            String id = createPrivateWork("ITest Private Edit Foreign", reader);
            mockMvc.perform(put("/my/works/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Hijack\",\"synopsis\":\"\"}").with(auth(readerB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void update_nonexistent_returns404() throws Exception {
            mockMvc.perform(put("/my/works/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Ghost\",\"synopsis\":\"\"}").with(auth(reader)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteVolume_byNonOwner_returns403() throws Exception {
            String id = createPrivateWork("ITest Private DelVol Foreign", reader);
            uploadVolume("/my/works", id, 1, reader);
            UUID volId = volumeRepository.findByWorkId(UUID.fromString(id)).get(0).getId();

            mockMvc.perform(delete("/my/works/{id}/volumes/{vid}", id, volId).with(auth(readerB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void deleteVolume_asOwner_returns200ThenSecondReturns404() throws Exception {
            String id = createPrivateWork("ITest Private DelVol", reader);
            uploadVolume("/my/works", id, 1, reader);
            uploadVolume("/my/works", id, 2, reader);
            UUID volId = volumeRepository.findByWorkId(UUID.fromString(id)).stream()
                    .filter(v -> v.getVolumeNumber() == 2).findFirst().orElseThrow().getId();

            mockMvc.perform(delete("/my/works/{id}/volumes/{vid}", id, volId).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.volumes.length()").value(1));
            mockMvc.perform(delete("/my/works/{id}/volumes/{vid}", id, volId).with(auth(reader)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void delete_byNonOwner_returns403() throws Exception {
            String id = createPrivateWork("ITest Private Del Foreign", reader);
            mockMvc.perform(delete("/my/works/{id}", id).with(auth(readerB)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void delete_asOwner_returns204ThenSecondReturns404() throws Exception {
            String id = createPrivateWork("ITest Private Del", reader);
            mockMvc.perform(delete("/my/works/{id}", id).with(auth(reader)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/my/works/{id}", id).with(auth(reader)))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  4.5. objectName vinculado ao mangá (ADR-40)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class VolumeObjectNameSecurity {

        @Test
        void finalizeVolume_pendingObjectNameFromAnotherWork_returns400() throws Exception {
            String idA = createPrivateWork("ITest FIND002 Work A", reader);
            String idB = createPrivateWork("ITest FIND002 Work B", reader);

            String objectNameForA = requestUploadUrl("/my/works", idA, 1, reader);

            finalizeVolume("/my/works", idB, objectNameForA, 1, reader)
                    .andExpect(status().isBadRequest());
        }

        @Test
        void finalizeVolume_alreadyFinalizedObjectNameLeakedFromPublicVolume_returns400() throws Exception {
            // objectName finalizado ("volumes/...") de um volume PÚBLICO, do jeito que
            // vaza pela URL assinada de leitura — reaproveitado para finalizar um volume
            // num mangá PRIVADO qualquer.
            String publicId = createPublicWork("ITest FIND002 Public", collab);
            uploadVolume("/works", publicId, 1, collab);
            String leakedObjectName =
                    volumeRepository.findByWorkId(UUID.fromString(publicId)).get(0).getFileUrl();

            String privateId = createPrivateWork("ITest FIND002 Private", reader);

            finalizeVolume("/my/works", privateId, leakedObjectName, 1, reader)
                    .andExpect(status().isBadRequest());
        }

        @Test
        void uploadAndFinalize_movesObjectFromPendingPrefixToFinalVolumesPrefix() throws Exception {
            String id = createPrivateWork("ITest FIND002 HappyPath", reader);

            String pendingObjectName = requestUploadUrl("/my/works", id, 1, reader);
            org.assertj.core.api.Assertions.assertThat(pendingObjectName)
                    .matches("^pending/volumes/" + id + "/[0-9a-fA-F-]{36}\\.pdf$");

            finalizeVolume("/my/works", id, pendingObjectName, 1, reader)
                    .andExpect(status().isCreated());

            String storedFileUrl =
                    volumeRepository.findByWorkId(UUID.fromString(id)).get(0).getFileUrl();
            org.assertj.core.api.Assertions.assertThat(storedFileUrl)
                    .matches("^volumes/" + id + "/[0-9a-fA-F-]{36}\\.pdf$");

            verify(storageClient).move(eq(pendingObjectName), eq(storedFileUrl));
        }

        @Test
        void finalizeVolume_sameObjectNameTwice_returns404() throws Exception {
            String id = createPrivateWork("ITest Private Finalize Twice", reader);
            String obj = uploadVolume("/my/works", id, 1, reader);
            when(storageClient.getFileMetadata(eq(obj)))
                    .thenThrow(new StorageObjectNotFoundException("Objeto não encontrado no GCS: " + obj));

            finalizeVolume("/my/works", id, obj, 1, reader)
                    .andExpect(status().isNotFound());
        }

        @Test
        void finalizeVolume_duplicateVolumeNumber_doesNotMoveObjectOutOfPending() throws Exception {
            // se o agregado rejeitar o volume, o objeto tem que ficar em pending/ (limpo pela
            // lifecycle rule) — movido para volumes/ ele viraria órfão permanente
            String id = createPrivateWork("ITest FIND002 Duplicate", reader);
            uploadVolume("/my/works", id, 1, reader);

            String pendingObjectName = requestUploadUrl("/my/works", id, 2, reader);

            finalizeVolume("/my/works", id, pendingObjectName, 1, reader)
                    .andExpect(status().isConflict());

            verify(storageClient, never()).move(eq(pendingObjectName), any());
        }

        @Test
        void deleteVolume_fileUrlSharedWithAnotherVolume_doesNotDeleteFile() throws Exception {
            // Simula dado legado (pré-ADR-40): dois volumes de mangás diferentes apontando
            // para o MESMO objeto físico, algo que só era possível quando o finalize
            // aceitava qualquer objectName do cliente.
            String sharedObjectName = "volumes/legacy-shared-" + UUID.randomUUID() + ".pdf";

            String idA = createPrivateWork("ITest FIND002 Shared A", reader);
            String idB = createPrivateWork("ITest FIND002 Shared B", reader);

            // work.addVolume() exige a coleção volumes carregada (sessão do Hibernate aberta);
            // como os mangás aqui vêm de um findById fora de transação, o Volume é construído
            // e salvo direto — mesmo resultado no banco, sem depender de proxy lazy.
            Work workA = workRepository.findById(UUID.fromString(idA)).orElseThrow();
            volumeRepository.save(new Volume(workA, 1, sharedObjectName, "hash-shared-a", 1024L, reader.getId()));

            Work workB = workRepository.findById(UUID.fromString(idB)).orElseThrow();
            volumeRepository.save(new Volume(workB, 1, sharedObjectName, "hash-shared-b", 1024L, reader.getId()));

            UUID volIdA = volumeRepository.findByWorkId(UUID.fromString(idA)).get(0).getId();

            mockMvc.perform(delete("/my/works/{id}/volumes/{vid}", idA, volIdA).with(auth(reader)))
                    .andExpect(status().isOk());

            verify(storageClient, never()).delete(eq(sharedObjectName));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  5. Promote  (test-phase5.sh §7)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Promote {

        @Test
        void promote_asReader_returns403() throws Exception {
            String id = createPrivateWork("ITest Promote Reader", reader);
            mockMvc.perform(post("/my/works/{id}/promote", id).with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void promote_asCollaborator_movesToPublicCatalog() throws Exception {
            String id = createPrivateWork("ITest Promote Collab", collab);

            mockMvc.perform(post("/my/works/{id}/promote", id).with(auth(collab)))
                    .andExpect(status().isOk());

            // some da coleção privada
            mockMvc.perform(get("/my/works").with(auth(collab)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
            // aparece na biblioteca pública
            mockMvc.perform(get("/works").param("title", "ITest Promote Collab").with(auth(collab)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void shouldKeepSlug_whenPromotedWorkHasNoSlugConflict() throws Exception {
            // Arrange
            String id = createPrivateWork("ITest Slug Promovido", collab);

            // Act
            mockMvc.perform(post("/my/works/{id}/promote", id).with(auth(collab)))
                    .andExpect(status().isOk());

            // Assert
            org.assertj.core.api.Assertions.assertThat(
                            workRepository.findById(UUID.fromString(id)).orElseThrow().getSlug())
                    .isEqualTo("itest-slug-promovido");
        }

        @Test
        void promote_titleAlreadyPublic_returns409() throws Exception {
            createPublicWork("ITest Promote Conflict", collab);
            String privateId = createPrivateWork("ITest Promote Conflict", collab);

            mockMvc.perform(post("/my/works/{id}/promote", privateId).with(auth(collab)))
                    .andExpect(status().isConflict());
        }

        @Test
        void promote_volumeHashAlreadyPublic_returns409() throws Exception {
            // mangá público com volume de hash conhecido
            String publicId = createPublicWork("ITest Hash Public", collab);
            String publicObj = uploadVolume("/works", publicId, 1, collab); // hash = fakeMd5(publicObj)

            // mangá privado cujo volume tem o MESMO hash
            String privateId = createPrivateWork("ITest Hash Private", collab);
            String privObj = requestUploadUrl("/my/works", privateId, 1, collab);
            when(storageClient.getFileMetadata(eq(privObj)))
                    .thenReturn(new StorageClient.FileMetadata(fakeMd5(publicObj), 1024L));
            finalizeVolume("/my/works", privateId, privObj, 1, collab).andExpect(status().isCreated());

            mockMvc.perform(post("/my/works/{id}/promote", privateId).with(auth(collab)))
                    .andExpect(status().isConflict());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  5b. Slug, capa e ordem dos volumes — rede de segurança antes do rename
    //      Work → Work e da troca de volume por capítulo
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class SlugCoverAndOrdering {

        @Test
        void shouldAppendNumericSuffix_whenPrivateTitleRepeatsAnExistingSlug() throws Exception {
            // Arrange
            String firstId = createPrivateWork("ITest Slug Repetido", collab);

            // Act
            String secondId = createPrivateWork("ITest Slug Repetido", collab);

            // Assert
            assertThat(workRepository.findById(UUID.fromString(firstId)).orElseThrow().getSlug())
                    .isEqualTo("itest-slug-repetido");
            assertThat(workRepository.findById(UUID.fromString(secondId)).orElseThrow().getSlug())
                    .isEqualTo("itest-slug-repetido-2");
        }

        @Test
        void shouldStoreCoverUnderCoversAndReturnSignedUrl_whenPrivateWorkIsCreatedWithCover() throws Exception {
            // Arrange
            String pngBase64 = "data:image/png;base64,"
                    + java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

            // Act
            ResultActions result = mockMvc.perform(post("/my/works")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Capa\",\"coverBase64\":\"" + pngBase64 + "\"}")
                    .with(auth(collab)));

            // Assert
            result.andExpect(status().isCreated())
                    .andExpect(jsonPath("$.coverUrl").value(FAKE_URL.toString()));
            verify(storageClient).upload(any(), startsWith("covers/"), eq("image/png"), eq(3L));
        }

        @Test
        void shouldReturn400AndUploadNothing_whenCoverTypeIsNotAllowed() throws Exception {
            // Arrange
            String htmlBase64 = "data:text/html;base64,"
                    + java.util.Base64.getEncoder().encodeToString("<p>x</p>".getBytes());

            // Act
            ResultActions result = mockMvc.perform(post("/my/works")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"ITest Capa Invalida\",\"coverBase64\":\"" + htmlBase64 + "\"}")
                    .with(auth(collab)));

            // Assert
            result.andExpect(status().isBadRequest());
            verify(storageClient, never()).upload(any(), anyString(), anyString(), anyLong());
        }

        @Test
        void shouldListVolumesByAscendingNumber_whenVolumesWereUploadedOutOfOrder() throws Exception {
            // Arrange
            String id = createPublicWork("ITest Ordem Volumes", collab);
            uploadVolume("/works", id, 3, collab);
            uploadVolume("/works", id, 1, collab);
            uploadVolume("/works", id, 2, collab);

            // Act
            ResultActions result = mockMvc.perform(get("/works/{id}", id).with(auth(reader)));

            // Assert
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.volumes[0].volumeNumber").value(1))
                    .andExpect(jsonPath("$.volumes[1].volumeNumber").value(2))
                    .andExpect(jsonPath("$.volumes[2].volumeNumber").value(3));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  6. Submissão → aprovação / rejeição  (fluxo de moderação)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Submission {

        @Test
        void submit_asOwner_setsPending() throws Exception {
            String id = createPrivateWork("ITest Submit One", reader);
            mockMvc.perform(post("/my/works/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.submissionStatus").value("PENDING"));
        }

        @Test
        void submit_whenAlreadyPending_returns409() throws Exception {
            String id = createPrivateWork("ITest Submit Twice", reader);
            mockMvc.perform(post("/my/works/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/my/works/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isConflict());
        }

        @Test
        void listPending_asReader_returns403() throws Exception {
            mockMvc.perform(get("/admin/submissions").with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void listPending_asAdmin_showsSubmitted() throws Exception {
            String id = createPrivateWork("ITest Submit Listed", reader);
            mockMvc.perform(post("/my/works/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/admin/submissions").with(auth(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        }

        @Test
        void approve_makesPublic() throws Exception {
            String id = createPrivateWork("ITest Submit Approve", reader);
            mockMvc.perform(post("/my/works/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/admin/submissions/{id}/approve", id).with(auth(admin)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/works").param("title", "ITest Submit Approve").with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void approve_persistsApprovedStatus() throws Exception {
            // round-trip real: a coluna é o enum nativo work_submission_status (V24)
            String id = createPrivateWork("ITest Submit Approved Status", reader);
            mockMvc.perform(post("/my/works/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/admin/submissions/{id}/approve", id).with(auth(admin)))
                    .andExpect(status().isNoContent());

            Work approved = workRepository.findById(UUID.fromString(id)).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(approved.getSubmissionStatus())
                    .isEqualTo(com.buruna.work.domain.WorkSubmissionStatus.APPROVED);
        }

        @Test
        void promote_withPendingSubmission_removesItFromReviewQueue() throws Exception {
            String id = createPrivateWork("ITest Submit Then Promote", collab);
            mockMvc.perform(post("/my/works/{id}/submit", id).with(auth(collab)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/my/works/{id}/promote", id).with(auth(collab)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/admin/submissions").with(auth(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").isEmpty());
        }

        @Test
        void approve_asReader_returns403() throws Exception {
            String id = createPrivateWork("ITest Submit Approve Forbidden", reader);
            mockMvc.perform(post("/my/works/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/admin/submissions/{id}/approve", id).with(auth(reader)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void reject_setsRejectedWithReason() throws Exception {
            String id = createPrivateWork("ITest Submit Reject", reader);
            mockMvc.perform(post("/my/works/{id}/submit", id).with(auth(reader)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/admin/submissions/{id}/reject", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rejectionReason\":\"qualidade insuficiente\"}").with(auth(admin)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/my/works/{id}", id).with(auth(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.submissionStatus").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReason").value("qualidade insuficiente"));
        }

        @Test
        void approve_whenNotPending_returns400() throws Exception {
            String id = createPrivateWork("ITest Submit NotPending", reader);
            mockMvc.perform(post("/admin/submissions/{id}/approve", id).with(auth(admin)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  7. Deleção de conta (DELETE /auth/account) — efeito no conteúdo do dono
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class AccountDeletion {

        @Test
        void shouldKeepPublicContentAndDeletePrivateCollection_whenOwnerDeletesAccount() throws Exception {
            User owner = buildUser("owner-del@work.test", "workOwnerDel", Role.COLLABORATOR);
            owner.changePassword(passwordEncoder.encode("Password@123"));
            owner = userRepository.save(owner);

            String publicId = createPublicWork("ITest Del Public", owner);
            uploadVolume("/works", publicId, 1, owner);
            String publicFile = volumeRepository.findByWorkId(UUID.fromString(publicId)).get(0).getFileUrl();
            String privateId = createPrivateWork("ITest Del Private", owner);
            uploadVolume("/my/works", privateId, 1, owner);
            String privateFile = volumeRepository.findByWorkId(UUID.fromString(privateId)).get(0).getFileUrl();

            mockMvc.perform(delete("/auth/account").with(auth(owner))
                            .header("X-Forwarded-For", "10.20.30.40")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"Password@123\"}"))
                    .andExpect(status().isNoContent());

            org.assertj.core.api.Assertions.assertThat(workRepository.findById(UUID.fromString(privateId))).isEmpty();
            org.assertj.core.api.Assertions.assertThat(workRepository.findById(UUID.fromString(publicId))).isPresent();
            org.assertj.core.api.Assertions.assertThat(volumeRepository.findByWorkId(UUID.fromString(publicId))).hasSize(1);
            verify(storageClient).delete(privateFile);
            verify(storageClient, never()).delete(publicFile);
        }
    }
}
