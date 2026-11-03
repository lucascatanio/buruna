package com.buruna.sourcing;

import com.buruna.identity.domain.Email;
import com.buruna.identity.domain.Quota;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.domain.Username;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.source.ExternalSource;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.ExternalWorkNotFoundException;
import com.buruna.shared.source.SourceChapterInfo;
import com.buruna.shared.source.SourceContentRating;
import com.buruna.shared.source.SourceGroup;
import com.buruna.shared.source.SourcePage;
import com.buruna.shared.source.SourceTag;
import com.buruna.shared.source.SourceTagGroup;
import com.buruna.shared.source.SourceWorkDetails;
import com.buruna.shared.source.SourceWorkStatus;
import com.buruna.shared.source.SourceWorkSummary;
import com.buruna.shared.source.WorkMetadataSource;
import com.buruna.shared.source.mangadownloader.MangaDownloaderClient;
import com.buruna.shared.source.mangadownloader.SiteListing;
import com.buruna.shared.storage.StorageClient;
import com.buruna.sourcing.application.SourcingRunner;
import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.SourceChapterState;
import com.buruna.sourcing.domain.SourceLease;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.persistence.SourceChapterRepository;
import com.buruna.sourcing.persistence.SourceLeaseRepository;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterNumber;
import com.buruna.work.domain.ChapterPage;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.domain.Tag;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkFormat;
import com.buruna.work.persistence.ChapterRepository;
import com.buruna.work.persistence.WorkRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Importação do MangaDex ponta a ponta (ADR-47 e ADR-48). A fonte e o storage são mockados. No
 * profile local a rodada do executor acontece na própria requisição, depois do commit, então o
 * import já volta com os capítulos baixados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class SourcingIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String EXTERNAL_ID = "md-obra-1";
    private static final OffsetDateTime MARCH = OffsetDateTime.parse("2026-03-01T00:00:00Z");
    private static final OffsetDateTime MAY = OffsetDateTime.parse("2026-05-01T00:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @Autowired ChapterRepository chapterRepository;
    @Autowired TrackedWorkRepository trackedWorkRepository;
    @Autowired SourceChapterRepository sourceChapterRepository;
    @Autowired SourceLeaseRepository leaseRepository;
    @Autowired SourcingRunner runner;
    @Autowired TransactionTemplate transactionTemplate;
    @MockitoBean ExternalSource source;
    @MockitoBean WorkMetadataSource metadataSource;
    @MockitoBean MangaDownloaderClient siteClient;
    @MockitoBean StorageClient storageClient;
    @MockitoBean EmailService emailService;

    User admin;
    User reader;
    byte[] png;

    @BeforeEach
    void setUp() throws Exception {
        png = png();
        transactionTemplate.executeWithoutResult(tx -> {
            sourceChapterRepository.deleteAll();
            trackedWorkRepository.deleteAll();
            chapterRepository.deleteAll();
            workRepository.deleteAll();
            for (SourceName source : SourceName.values()) {
                leaseRepository.lockBySource(source)
                        .filter(lease -> lease.getHolder() != null)
                        .ifPresent(lease -> lease.release(lease.getHolder()));
            }
        });
        userRepository.deleteAllInBatch();
        admin = userRepository.save(user("admin@sourcing.test", "sourcingAdmin", Role.ADMIN));
        reader = userRepository.save(user("reader@sourcing.test", "sourcingReader", Role.READER));

        when(source.fetchWork(EXTERNAL_ID)).thenReturn(details());
        when(source.listChapters(eq(EXTERNAL_ID), any())).thenReturn(List.of(
                // cap. 1: o grupo preferido (A) e outro mais novo (B)
                version("c1-a", "1", "gA", "Grupo A", MARCH),
                version("c1-b", "1", "gB", "Grupo B", MAY),
                // cap. 2: sem o grupo preferido; entra a mais recente (C)
                version("c2-b", "2", "gB", "Grupo B", MARCH),
                version("c2-c", "2", "gC", "Grupo C", MAY),
                // oneshot sem número e número que não cabe na coluna
                new SourceChapterInfo("os", "pt-BR", null, null, "Especial", List.of(new SourceGroup("gB", "Grupo B")),
                        2, MARCH, MARCH),
                version("c3", "10.125", "gA", "Grupo A", MARCH)));
        when(source.fetchPages(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return List.of(new SourcePage("https://node.test/data/" + id + "/1.png", "https://node.test/ds/" + id + "/1.png"),
                    new SourcePage("https://node.test/data/" + id + "/2.png", "https://node.test/ds/" + id + "/2.png"));
        });
        when(source.download(anyString(), anyLong())).thenAnswer(invocation -> png);
        when(storageClient.upload(any(), anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    static User user(String email, String username, Role role) {
        User u = User.register(Email.of(email), Username.of(username),
                "$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC", "test",
                Quota.of(BigDecimal.ONE));
        u.changeRole(role);
        u.changeStatus(UserStatus.ACTIVE);
        return u;
    }

    RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    static SourceWorkDetails details() {
        return details(EXTERNAL_ID);
    }

    static SourceWorkDetails details(String externalId) {
        return new SourceWorkDetails(externalId, "Obra da Fonte", List.of("Source Work"),
                Map.of("en", "Synopsis", "pt-BR", "Sinopse"), "ko", SourceWorkStatus.ONGOING, 2021,
                SourceContentRating.EROTICA,
                List.of(new SourceTag("Action", SourceTagGroup.GENRE), new SourceTag("Long Strip", SourceTagGroup.FORMAT)),
                true, null, "https://mangadex.org/title/" + externalId, "seinen", List.of("pt-BR", "en"));
    }

    static SourceChapterInfo version(String id, String number, String groupId, String groupName, OffsetDateTime at) {
        return new SourceChapterInfo(id, "pt-BR", number, null, null, List.of(new SourceGroup(groupId, groupName)),
                2, at, at);
    }

    String importBody(String linkWorkId) {
        return """
                {"externalId":"%s","languages":[{"language":"pt-br","preferredGroupId":"gA","preferredGroupName":"Grupo A"}]%s}
                """.formatted(EXTERNAL_ID, linkWorkId == null ? "" : ",\"linkWorkId\":\"" + linkWorkId + "\"");
    }

    String importWork(String linkWorkId) throws Exception {
        String body = mockMvc.perform(post("/admin/sourcing/imports").contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(linkWorkId)).with(auth(admin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.workId");
    }

    List<Chapter> chaptersOf(String workId) {
        return transactionTemplate.execute(tx -> chapterRepository.findAll().stream()
                .filter(chapter -> chapter.getWorkId().equals(UUID.fromString(workId)))
                .peek(chapter -> chapter.getPages().size())
                .sorted(Comparator.comparing(SourcingIntegrationTest::numberOf, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList());
    }

    static BigDecimal numberOf(Chapter chapter) {
        return chapter.getNumber().map(ChapterNumber::value).orElse(null);
    }

    Map<String, SourceChapterState> versionStates() {
        return sourceChapterRepository.findAll().stream()
                .collect(Collectors.toMap(SourceChapter::getExternalId, SourceChapter::getState));
    }

    // ── Import ──────────────────────────────────────────────────────────────

    @Test
    void shouldCreateWorkAndPublishChosenVersions_whenAdminImports() throws Exception {
        String workId = importWork(null);

        Work work = transactionTemplate.execute(tx -> {
            Work w = workRepository.findById(UUID.fromString(workId)).orElseThrow();
            w.getTags().size();
            w.getContentWarnings().size();
            return w;
        });
        assertThat(work.getTitle()).isEqualTo("Obra da Fonte");
        assertThat(work.getFormat()).isEqualTo(WorkFormat.MANHWA);
        assertThat(work.getSynopsis()).isEqualTo("Sinopse");
        assertThat(work.isPublic()).isTrue();
        assertThat(work.getContentWarnings()).containsExactly("NSFW");
        assertThat(work.getTags()).extracting(Tag::getName)
                .contains("Ação", "Webtoon", "Seinen", "Manhwa");

        List<Chapter> chapters = chaptersOf(workId);
        assertThat(chapters).hasSize(4).allMatch(chapter -> chapter.getStatus() == ChapterStatus.PUBLISHED);
        assertThat(numberOf(chapters.get(0))).isEqualByComparingTo("1");
        assertThat(chapters.get(0).getScanlationGroup()).contains("Grupo A");
        assertThat(numberOf(chapters.get(1))).isEqualByComparingTo("2");
        assertThat(chapters.get(1).getScanlationGroup()).contains("Grupo C");
        assertThat(chapters).extracting(chapter -> chapter.getLabel().orElse(null)).contains("Especial", "Cap. 10.125");
        ChapterPage page = chapters.get(0).getPages().get(0);
        assertThat(page.getWidth()).isEqualTo(20);
        assertThat(page.getHeight()).isEqualTo(30);
        assertThat(page.getDataSaverObjectName()).hasValueSatisfying(name -> assertThat(name).contains("/ds/"));

        assertThat(versionStates()).containsAllEntriesOf(Map.of(
                "c1-a", SourceChapterState.READY, "c1-b", SourceChapterState.IGNORED,
                "c2-b", SourceChapterState.IGNORED, "c2-c", SourceChapterState.READY,
                "os", SourceChapterState.READY, "c3", SourceChapterState.READY));

        mockMvc.perform(get("/works/" + workId + "/source").with(auth(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MangaDex"))
                .andExpect(jsonPath("$.url").value("https://mangadex.org/title/" + EXTERNAL_ID));
        mockMvc.perform(get("/admin/sourcing/tracked").with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chapterCounts.READY").value(4))
                .andExpect(jsonPath("$[0].chapterCounts.IGNORED").value(2))
                .andExpect(jsonPath("$[0].languages[0].language").value("pt-BR"));
    }

    @Test
    void shouldReturnConflict_whenWorkIsAlreadyTracked() throws Exception {
        importWork(null);

        mockMvc.perform(post("/admin/sourcing/imports").contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(null)).with(auth(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAttachChaptersToExistingWork_whenLinkWorkIdIsGiven() throws Exception {
        String body = mockMvc.perform(post("/works").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Obra Legada\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                        .with(auth(admin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String legacyId = JsonPath.read(body, "$.id");

        String workId = importWork(legacyId);

        assertThat(workId).isEqualTo(legacyId);
        assertThat(workRepository.count()).isEqualTo(1);
        assertThat(chaptersOf(legacyId)).hasSize(4);
    }

    @Test
    void shouldFailOnlyThatVersion_whenItsPagesDoNotDownload() throws Exception {
        when(source.fetchPages("c2-c")).thenThrow(new ExternalSourceException("nó fora do ar"));

        String workId = importWork(null);

        assertThat(chaptersOf(workId)).hasSize(3)
                .noneMatch(chapter -> numberOf(chapter) != null && numberOf(chapter).compareTo(BigDecimal.TWO) == 0);
        SourceChapter failed = sourceChapterRepository.findAll().stream()
                .filter(version -> version.getExternalId().equals("c2-c")).findFirst().orElseThrow();
        assertThat(failed.getState()).isEqualTo(SourceChapterState.FAILED);
        assertThat(failed.getLastError()).contains("nó fora do ar");
        assertThat(failed.getChapterId()).isEmpty();
    }

    @Test
    void shouldLeaveVersionsPending_whenLeaseIsHeldByAnotherRun() throws Exception {
        transactionTemplate.executeWithoutResult(tx -> leaseRepository.lockBySource(SourceName.MANGADEX)
                .orElseThrow().tryAcquire("outra-execucao", OffsetDateTime.now(), Duration.ofMinutes(10)));

        String workId = importWork(null);

        assertThat(chaptersOf(workId)).isEmpty();
        assertThat(trackedWorkRepository.findAll().get(0).needsListing()).isTrue();

        transactionTemplate.executeWithoutResult(tx -> leaseRepository.lockBySource(SourceName.MANGADEX)
                .orElseThrow().release("outra-execucao"));
        runner.run("rodada-seguinte", SourceName.MANGADEX);

        assertThat(chaptersOf(workId)).hasSize(4);
        assertThat(leaseRepository.findById(SourceName.MANGADEX).map(SourceLease::getHolder)).isEmpty();
    }

    @Test
    void shouldReturnForbidden_whenReaderTriesToImport() throws Exception {
        mockMvc.perform(post("/admin/sourcing/imports").contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(null)).with(auth(reader)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnNoContent_whenWorkDidNotComeFromASource() throws Exception {
        mockMvc.perform(get("/works/" + UUID.randomUUID() + "/source").with(auth(reader)))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldShowAvailabilityAndGroups_whenAdminPicksALanguage() throws Exception {
        when(source.countPublishedChapters(EXTERNAL_ID, "pt-BR")).thenReturn(119);

        mockMvc.perform(get("/admin/sourcing/works/" + EXTERNAL_ID + "/availability").param("language", "pt-br")
                        .with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("pt-BR"))
                .andExpect(jsonPath("$.availableChapters").value(6))
                .andExpect(jsonPath("$.publishedChapters").value(119))
                .andExpect(jsonPath("$.groups[0].id").value("gB"))
                .andExpect(jsonPath("$.groups[0].chapterCount").value(3));
    }

    // ── Metadados do AniList (ADR-54) ───────────────────────────────────────

    @Test
    void shouldListAniListResults_whenAdminSearchesMetadata() throws Exception {
        when(metadataSource.search("frieren", 20)).thenReturn(List.of(new SourceWorkSummary("118586",
                "Sousou no Frieren", 2020, SourceContentRating.SAFE, "ja", SourceWorkStatus.ONGOING, List.of(),
                "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/x.jpg",
                "https://anilist.co/manga/118586")));

        mockMvc.perform(get("/admin/sourcing/metadata/search").param("q", " frieren ").with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalId").value("118586"))
                .andExpect(jsonPath("$[0].title").value("Sousou no Frieren"))
                .andExpect(jsonPath("$[0].status").value("ONGOING"));
    }

    @Test
    void shouldPreviewWorkInCatalogVocabulary_whenAdminOpensAniListWork() throws Exception {
        when(metadataSource.fetchWork("118586")).thenReturn(new SourceWorkDetails("118586", "Sousou no Frieren",
                List.of("Frieren: Beyond Journey's End"), Map.of("en", "An elf mage."), "ja",
                SourceWorkStatus.ONGOING, 2020, SourceContentRating.SAFE,
                List.of(new SourceTag("Adventure", SourceTagGroup.GENRE), new SourceTag("Magic", SourceTagGroup.THEME)),
                false, "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/x.jpg",
                "https://anilist.co/manga/118586", "shounen", List.of()));

        mockMvc.perform(get("/admin/sourcing/metadata/works/118586").with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("MANGA"))
                .andExpect(jsonPath("$.originCountry").value("Japan"))
                .andExpect(jsonPath("$.synopsis").value("An elf mage."))
                .andExpect(jsonPath("$.tags[0].name").value("Aventura"))
                .andExpect(jsonPath("$.tags[1].name").value("Magia"))
                .andExpect(jsonPath("$.tags[2].name").value("Shounen"));
    }

    @Test
    void shouldReturnNotFound_whenAniListHasNoSuchWork() throws Exception {
        when(metadataSource.fetchWork("999")).thenThrow(new ExternalWorkNotFoundException("999"));

        mockMvc.perform(get("/admin/sourcing/metadata/works/999").with(auth(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Obra não encontrada no AniList: 999"));
    }

    @Test
    void shouldNameAniList_whenMetadataSearchFails() throws Exception {
        when(metadataSource.search(anyString(), anyInt())).thenThrow(new ExternalSourceException("429"));

        mockMvc.perform(get("/admin/sourcing/metadata/search").param("q", "frieren").with(auth(admin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("O AniList não respondeu. Tente de novo em alguns minutos."));
    }

    @Test
    void shouldReturnNotFound_whenMetadataCoverIsRefused() throws Exception {
        when(metadataSource.download(anyString(), anyLong())).thenThrow(new ExternalSourceException("recusada"));

        mockMvc.perform(get("/admin/sourcing/metadata/cover").param("url", "https://evil.test/x.jpg").with(auth(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnForbidden_whenReaderSearchesMetadata() throws Exception {
        mockMvc.perform(get("/admin/sourcing/metadata/search").param("q", "frieren").with(auth(reader)))
                .andExpect(status().isForbidden());
    }

    // ── Sites do manga-downloader (ADR-54) ──────────────────────────────────

    private static final String SITE_URL = "https://site.test/manga/obra";

    static SiteListing.Chapter siteChapter(String id, double number, String language, String group, boolean locked) {
        String label = "Capítulo " + BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        return new SiteListing.Chapter(id, number, false, label, language, group,
                group.isEmpty() ? List.of() : List.of(group), 0, locked);
    }

    static SiteListing siteListing() {
        return new SiteListing("Obra do Site", "PlainHTML", List.of(
                siteChapter("c1-alpha", 1, "en", "Alpha", false),
                siteChapter("c1-beta", 1, "en", "Beta", false),
                // sem id no site: a chave sai de número, idioma e grupo
                siteChapter("", 2, "en", "Alpha", false),
                siteChapter("c3", 3, "en", "Alpha", true),
                siteChapter("c1-es", 1, "es", "Alpha", false),
                new SiteListing.Chapter("extra", 0, true, "Extra", "en", "Alpha", List.of("Alpha"), 0, false)),
                null, null, "");
    }

    // o manga-downloader grava um CBZ com duas páginas no diretório pedido
    void siteDownloadsWriteArchives() {
        when(siteClient.download(anyString(), anyString(), anyString(), anyString(), any(Path.class)))
                .thenAnswer(invocation -> {
                    Path archive = ((Path) invocation.getArgument(4)).resolve(invocation.getArgument(1) + ".cbz");
                    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                        for (String name : List.of("001.png", "002.png")) {
                            zip.putNextEntry(new ZipEntry(name));
                            zip.write(png);
                            zip.closeEntry();
                        }
                    }
                    return archive;
                });
    }

    String createWork() throws Exception {
        String body = mockMvc.perform(post("/works").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Obra Existente\",\"format\":\"MANGA\",\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                        .with(auth(admin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    String siteImportBody(String url, String linkWorkId) {
        return """
                {"source":"MANGA_DOWNLOADER","externalId":"%s","linkWorkId":"%s",
                 "languages":[{"language":"en","sourceLanguage":"en","preferredGroupId":"Alpha","preferredGroupName":"Alpha"}]}
                """.formatted(url, linkWorkId);
    }

    @Test
    void shouldPublishSiteChapters_whenAdminImportsSeriesIntoExistingWork() throws Exception {
        String workId = createWork();
        when(siteClient.list(SITE_URL)).thenReturn(siteListing());
        siteDownloadsWriteArchives();

        mockMvc.perform(post("/admin/sourcing/imports").contentType(MediaType.APPLICATION_JSON)
                        .content(siteImportBody(SITE_URL, workId)).with(auth(admin)))
                .andExpect(status().isCreated());

        List<Chapter> chapters = chaptersOf(workId);
        assertThat(chapters).hasSize(2).allMatch(chapter -> chapter.getStatus() == ChapterStatus.PUBLISHED);
        assertThat(numberOf(chapters.get(0))).isEqualByComparingTo("1");
        assertThat(chapters.get(0).getScanlationGroup()).contains("Alpha");
        assertThat(chapters.get(0).getPages()).hasSize(2);
        assertThat(chapters.get(0).getPages().get(0).getDataSaverObjectName()).isEmpty();
        // o rótulo "Capítulo 1" que a CLI monta não vira título
        assertThat(chapters.get(0).getTitle()).isEmpty();
        verify(siteClient).download(eq(SITE_URL), eq("1"), eq("en"), eq("Alpha"), any(Path.class));
        // bloqueado e espanhol (não acompanhado) nem entram; o extra sem número não tem como baixar
        assertThat(versionStates()).containsOnlyKeys("c1-alpha", "c1-beta", "sem-id:2|en|Alpha", "extra")
                .containsAllEntriesOf(Map.of("c1-alpha", SourceChapterState.READY, "c1-beta", SourceChapterState.IGNORED,
                        "sem-id:2|en|Alpha", SourceChapterState.READY, "extra", SourceChapterState.FAILED));
        // só o crédito do grupo, no capítulo: a página da obra não cita o site
        mockMvc.perform(get("/works/" + workId + "/source").with(auth(reader)))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldLeaveMangaDexSeriesForNextRound_whenMangaDexLeaseIsBusy() throws Exception {
        String workId = createWork();
        String url = "https://mangadex.org/title/abc/obra";
        when(siteClient.list(url)).thenReturn(siteListing());
        siteDownloadsWriteArchives();
        transactionTemplate.executeWithoutResult(tx -> leaseRepository.lockBySource(SourceName.MANGADEX).orElseThrow()
                .tryAcquire("sync-das-obras-antigas", OffsetDateTime.now(), Duration.ofHours(1)));

        mockMvc.perform(post("/admin/sourcing/imports").contentType(MediaType.APPLICATION_JSON)
                        .content(siteImportBody(url, workId)).with(auth(admin)))
                .andExpect(status().isCreated());

        verify(siteClient, never()).list(url);
        assertThat(chaptersOf(workId)).isEmpty();

        transactionTemplate.executeWithoutResult(tx -> leaseRepository.lockBySource(SourceName.MANGADEX).orElseThrow()
                .release("sync-das-obras-antigas"));
        runner.run("rodada-seguinte", SourceName.MANGA_DOWNLOADER);

        assertThat(chaptersOf(workId)).hasSize(2);
        assertThat(leaseRepository.findById(SourceName.MANGADEX).map(SourceLease::getHolder)).isEmpty();
        // série do mangadex.org: a AUP pede o crédito ao MangaDex
        mockMvc.perform(get("/works/" + workId + "/source").with(auth(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MangaDex"))
                .andExpect(jsonPath("$.url").value(url));
    }

    @Test
    void shouldListSiteSeries_whenAdminPastesUrl() throws Exception {
        when(siteClient.list(SITE_URL)).thenReturn(siteListing());

        mockMvc.perform(get("/admin/sourcing/site-listing").param("url", " " + SITE_URL + " ").with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Obra do Site"))
                .andExpect(jsonPath("$.tracked").value(false))
                .andExpect(jsonPath("$.languages[0].code").value("en"))
                .andExpect(jsonPath("$.languages[0].chapterCount").value(5))
                .andExpect(jsonPath("$.groups[0].id").value("Alpha"))
                .andExpect(jsonPath("$.chapters[3].locked").value(true))
                .andExpect(jsonPath("$.chapters[5].number").doesNotExist());
    }

    @Test
    void shouldExplainWhy_whenSiteCannotBeListed() throws Exception {
        when(siteClient.list(SITE_URL)).thenThrow(new ExternalSourceException("O manga-downloader falhou: Site not recognised"));

        mockMvc.perform(get("/admin/sourcing/site-listing").param("url", SITE_URL).with(auth(admin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Não deu para listar esta série. O manga-downloader falhou: Site not recognised"));
    }

    @Test
    void shouldRefuseSeriesUrl_whenItIsNotHttp() throws Exception {
        mockMvc.perform(get("/admin/sourcing/site-listing").param("url", "--output-dir=/tmp").with(auth(admin)))
                .andExpect(status().isBadRequest());

        verify(siteClient, never()).list(anyString());
    }

    @Test
    void shouldRefuseSiteImport_whenNoWorkIsLinked() throws Exception {
        mockMvc.perform(post("/admin/sourcing/imports").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MANGA_DOWNLOADER","externalId":"%s","languages":[{"language":"en","sourceLanguage":"en"}]}
                                """.formatted(SITE_URL)).with(auth(admin)))
                .andExpect(status().isBadRequest());

        assertThat(trackedWorkRepository.count()).isZero();
    }

    @Test
    void shouldReturnForbidden_whenReaderListsSiteSeries() throws Exception {
        mockMvc.perform(get("/admin/sourcing/site-listing").param("url", SITE_URL).with(auth(reader)))
                .andExpect(status().isForbidden());
    }

    // ── Obra criada pelo AniList (ADR-54) ───────────────────────────────────

    static SourceWorkDetails aniListDetails() {
        return new SourceWorkDetails("118586", "Sousou no Frieren", List.of("Frieren: Beyond Journey's End"),
                Map.of("en", "An elf mage."), "ja", SourceWorkStatus.ONGOING, 2020, SourceContentRating.SAFE,
                List.of(new SourceTag("Adventure", SourceTagGroup.GENRE), new SourceTag("Magic", SourceTagGroup.THEME)),
                false, "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/x.jpg",
                "https://anilist.co/manga/118586", "shounen", List.of());
    }

    String aniListImportBody(String url) {
        return """
                {"source":"MANGA_DOWNLOADER","externalId":"%s","aniListId":118586,"synopsis":"Uma elfa maga.",
                 "languages":[{"language":"en","sourceLanguage":"en","preferredGroupId":"Alpha","preferredGroupName":"Alpha"}]}
                """.formatted(url);
    }

    String importFromAniList(String url) throws Exception {
        // com capa, a resposta da obra criada traz a URL assinada dela
        when(storageClient.generateSignedUrl(anyString(), any())).thenReturn(URI.create("https://storage.test/capa").toURL());
        String body = mockMvc.perform(post("/admin/sourcing/imports").contentType(MediaType.APPLICATION_JSON)
                        .content(aniListImportBody(url)).with(auth(admin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.workId");
    }

    @Test
    void shouldCreateWorkFromAniList_whenSiteImportNamesAniListWork() throws Exception {
        when(metadataSource.fetchWork("118586")).thenReturn(aniListDetails());
        when(metadataSource.download(anyString(), anyLong())).thenReturn(png);
        when(siteClient.list(SITE_URL)).thenReturn(siteListing());
        siteDownloadsWriteArchives();

        String workId = importFromAniList(SITE_URL);

        Work work = transactionTemplate.execute(tx -> {
            Work w = workRepository.findById(UUID.fromString(workId)).orElseThrow();
            w.getTags().size();
            return w;
        });
        assertThat(work.getTitle()).isEqualTo("Sousou no Frieren");
        assertThat(work.getSynopsis()).isEqualTo("Uma elfa maga.");
        assertThat(work.getAniListId()).contains(118586);
        assertThat(work.getCoverUrl()).isNotBlank();
        assertThat(work.getTags()).extracting(Tag::getName).contains("Aventura", "Magia", "Shounen", "Mangá");
        assertThat(chaptersOf(workId)).hasSize(2);

        mockMvc.perform(get("/admin/sourcing/metadata/works/118586").with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogWork.id").value(workId))
                .andExpect(jsonPath("$.catalogWork.title").value("Sousou no Frieren"));
    }

    @Test
    void shouldAttachToSameWork_whenAniListWorkIsImportedFromAnotherSite() throws Exception {
        String otherSite = "https://outro.test/manga/frieren";
        when(metadataSource.fetchWork("118586")).thenReturn(aniListDetails());
        when(metadataSource.download(anyString(), anyLong())).thenReturn(png);
        when(siteClient.list(anyString())).thenReturn(siteListing());
        siteDownloadsWriteArchives();

        String first = importFromAniList(SITE_URL);
        String second = importFromAniList(otherSite);

        assertThat(second).isEqualTo(first);
        assertThat(workRepository.count()).isEqualTo(1);
        assertThat(trackedWorkRepository.count()).isEqualTo(2);
        // o segundo import achou a obra pelo id e nem foi ao AniList
        verify(metadataSource, times(1)).fetchWork("118586");
    }

    @Test
    void shouldRefuseSiteImport_whenBothWorkAndAniListAreGiven() throws Exception {
        String workId = createWork();

        mockMvc.perform(post("/admin/sourcing/imports").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MANGA_DOWNLOADER","externalId":"%s","linkWorkId":"%s","aniListId":118586,
                                 "languages":[{"language":"en","sourceLanguage":"en"}]}
                                """.formatted(SITE_URL, workId)).with(auth(admin)))
                .andExpect(status().isBadRequest());

        verify(metadataSource, never()).fetchWork(anyString());
        assertThat(trackedWorkRepository.count()).isZero();
    }

    @Test
    void shouldNameAniList_whenAniListWorkDoesNotExist() throws Exception {
        when(metadataSource.fetchWork("118586")).thenThrow(new ExternalWorkNotFoundException("118586"));

        mockMvc.perform(post("/admin/sourcing/imports").contentType(MediaType.APPLICATION_JSON)
                        .content(aniListImportBody(SITE_URL)).with(auth(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Obra não encontrada no AniList: 118586"));

        assertThat(workRepository.count()).isZero();
    }
}
