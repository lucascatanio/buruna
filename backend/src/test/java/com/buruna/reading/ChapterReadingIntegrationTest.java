package com.buruna.reading;

import com.buruna.identity.domain.Email;
import com.buruna.identity.domain.Quota;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.domain.Username;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.reading.persistence.ReadingHistoryRepository;
import com.buruna.reading.persistence.ReadingProgressRepository;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.storage.StorageClient;
import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterKind;
import com.buruna.work.domain.ChapterNumber;
import com.buruna.work.domain.ChapterPage;
import com.buruna.work.domain.Language;
import com.buruna.work.domain.Slug;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkFormat;
import com.buruna.work.domain.WorkStatusOrigin;
import com.buruna.work.domain.WorkStatusSite;
import com.buruna.work.persistence.ChapterRepository;
import com.buruna.work.persistence.WorkRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Leitura por capítulo (ADR-44): manifesto com URL por página, progresso com o total vindo do
 * servidor, progresso por obra, histórico e a lista de capítulos com idiomas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class ChapterReadingIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static final OffsetDateTime PUBLISHED_AT = OffsetDateTime.parse("2026-10-09T12:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @Autowired ChapterRepository chapterRepository;
    @Autowired ReadingProgressRepository progressRepository;
    @Autowired ReadingHistoryRepository historyRepository;
    @MockitoBean StorageClient storageClient;
    @MockitoBean EmailService emailService;

    User owner;
    User reader;
    Work publicWork;

    @BeforeEach
    void setUp() throws Exception {
        when(storageClient.generateSignedUrl(anyString(), any(Duration.class)))
                .thenAnswer(inv -> new URL("https://storage.example.com/" + inv.getArgument(0)));

        progressRepository.deleteAllInBatch();
        historyRepository.deleteAllInBatch();
        chapterRepository.deleteAll();
        workRepository.deleteAll();
        userRepository.deleteAllInBatch();

        owner = userRepository.save(user("owner@reading-chapters.test", "readingOwner", Role.COLLABORATOR));
        reader = userRepository.save(user("reader@reading-chapters.test", "readingReader", Role.READER));
        publicWork = publicWork("Obra Pública");
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

    Work publicWork(String title) {
        Work work = Work.createPublic(Slug.of(Slug.fromTitle(title).value() + "-" + UUID.randomUUID()), owner.getId());
        work.updateCatalogDetails(title, List.of(), null, WorkFormat.MANGA, null,
                WorkStatusOrigin.ONGOING, WorkStatusSite.INCOMPLETE, null, List.of(), Set.of());
        return workRepository.save(work);
    }

    Chapter published(Work work, String language, String number, int pageCount) {
        Chapter chapter = Chapter.register(work.getId(), Language.of(language), ChapterNumber.of(new BigDecimal(number)),
                null, null, "Grupo X", ChapterKind.PAGES, owner.getId());
        List<ChapterPage> pages = new ArrayList<>();
        for (int i = 1; i <= pageCount; i++) {
            pages.add(ChapterPage.of(i, "chapters/" + number + "/" + i + ".jpg", null, 800, 1200 + i, 1024));
        }
        chapter.publishPages(pages, PUBLISHED_AT);
        return chapterRepository.save(chapter);
    }

    Chapter processing(Work work, String language, String number) {
        return chapterRepository.save(Chapter.register(work.getId(), Language.of(language),
                ChapterNumber.of(new BigDecimal(number)), null, null, null, ChapterKind.PAGES, owner.getId()));
    }

    ResultActions saveProgress(Chapter chapter, int page, User u) throws Exception {
        return mockMvc.perform(post("/reader/chapters/{id}/progress", chapter.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPage\":" + page + "}").with(auth(u)));
    }

    // ── Manifesto ───────────────────────────────────────────────────────────

    @Test
    void shouldReturnSignedPagesAndNeighbors_whenReaderOpensAPublishedChapter() throws Exception {
        // Arrange
        Chapter first = published(publicWork, "pt-BR", "1", 2);
        Chapter second = published(publicWork, "pt-BR", "2", 3);
        Chapter third = published(publicWork, "pt-BR", "3", 1);
        published(publicWork, "en", "2.5", 1);

        // Act
        ResultActions result = mockMvc.perform(get("/reader/chapters/{id}", second.getId()).with(auth(reader)));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.pages", hasSize(3)))
                .andExpect(jsonPath("$.pages[0].url").value("https://storage.example.com/chapters/2/1.jpg"))
                .andExpect(jsonPath("$.pages[0].width").value(800))
                .andExpect(jsonPath("$.pages[2].height").value(1203))
                .andExpect(jsonPath("$.pages[0].dataSaverUrl").value(nullValue()))
                .andExpect(jsonPath("$.previousChapterId").value(first.getId().toString()))
                .andExpect(jsonPath("$.nextChapterId").value(third.getId().toString()))
                .andExpect(jsonPath("$.scanlationGroup").value("Grupo X"))
                .andExpect(jsonPath("$.urlsExpireAt").exists());
        assertThat(workRepository.findById(publicWork.getId()).orElseThrow().getViewCount()).isEqualTo(1);
    }

    @Test
    void shouldSkipHistoryAndViewCount_whenNextChapterIsPrefetched() throws Exception {
        // Arrange
        Chapter chapter = published(publicWork, "pt-BR", "1", 2);

        // Act
        ResultActions result = mockMvc.perform(get("/reader/chapters/{id}", chapter.getId())
                .param("prefetch", "true").with(auth(reader)));

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.pages", hasSize(2)));
        assertThat(historyRepository.count()).isZero();
        assertThat(workRepository.findById(publicWork.getId()).orElseThrow().getViewCount()).isZero();
    }

    @Test
    void shouldReturn404_whenChapterIsStillProcessing() throws Exception {
        Chapter chapter = processing(publicWork, "pt-BR", "1");

        mockMvc.perform(get("/reader/chapters/{id}", chapter.getId()).with(auth(reader)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn403_whenReaderOpensAChapterOfSomeoneElsesPrivateWork() throws Exception {
        // Arrange
        Work privateWork = workRepository.save(Work.createPrivate(
                Slug.of("privada-" + UUID.randomUUID()), "Privada", null, owner.getId()));
        Chapter chapter = published(privateWork, "pt-BR", "1", 1);

        // Act / Assert
        mockMvc.perform(get("/reader/chapters/{id}", chapter.getId()).with(auth(reader)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/reader/chapters/{id}", chapter.getId()).with(auth(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRecordChapterInHistory_whenChapterIsOpened() throws Exception {
        // Arrange
        Chapter chapter = published(publicWork, "pt-BR", "4", 1);
        mockMvc.perform(get("/reader/chapters/{id}", chapter.getId()).with(auth(reader)));

        // Act
        ResultActions result = mockMvc.perform(get("/reader/history").with(auth(reader)));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].chapterId").value(chapter.getId().toString()))
                .andExpect(jsonPath("$.content[0].chapterNumber").value(4))
                .andExpect(jsonPath("$.content[0].language").value("pt-BR"))
                .andExpect(jsonPath("$.content[0].volumeId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].workTitle").value("Obra Pública"));
    }

    // ── Progresso ───────────────────────────────────────────────────────────

    @Test
    void shouldMarkFinishedWithServerTotal_whenReaderReachesTheLastPage() throws Exception {
        // Arrange
        Chapter chapter = published(publicWork, "pt-BR", "1", 3);

        // Act
        ResultActions result = saveProgress(chapter, 3, reader);

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.chapterId").value(chapter.getId().toString()))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.finished").value(true));
    }

    @Test
    void shouldUpdateTheSameRow_whenProgressIsSavedTwice() throws Exception {
        // Arrange
        Chapter chapter = published(publicWork, "pt-BR", "1", 5);
        saveProgress(chapter, 1, reader);

        // Act
        saveProgress(chapter, 4, reader).andExpect(status().isOk());

        // Assert
        assertThat(progressRepository.findByUserIdAndChapterId(reader.getId(), chapter.getId()))
                .get().extracting(p -> p.getCurrentPage()).isEqualTo(4);
        assertThat(progressRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldReturn400_whenPageIsBeyondTheChapter() throws Exception {
        Chapter chapter = published(publicWork, "pt-BR", "1", 2);

        saveProgress(chapter, 3, reader).andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn204_whenChapterWasNeverRead() throws Exception {
        Chapter chapter = published(publicWork, "pt-BR", "1", 2);

        mockMvc.perform(get("/reader/chapters/{id}/progress", chapter.getId()).with(auth(reader)))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldListMostRecentFirst_whenReaderHasProgressInSeveralChapters() throws Exception {
        // Arrange
        Chapter first = published(publicWork, "pt-BR", "1", 2);
        Chapter second = published(publicWork, "pt-BR", "2", 2);
        saveProgress(second, 1, reader);
        saveProgress(first, 2, reader);

        // Act
        ResultActions result = mockMvc.perform(get("/reader/works/{id}/chapter-progress", publicWork.getId())
                .with(auth(reader)));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].chapterId").value(first.getId().toString()))
                .andExpect(jsonPath("$[0].finished").value(true));
    }

    // ── Lista de capítulos ──────────────────────────────────────────────────

    @Test
    void shouldListOnlyPublishedInReadingOrder_whenReaderListsChapters() throws Exception {
        // Arrange
        published(publicWork, "pt-BR", "10", 1);
        published(publicWork, "pt-BR", "2", 1);
        processing(publicWork, "pt-BR", "11");
        published(publicWork, "en", "1", 1);

        // Act
        ResultActions result = mockMvc.perform(get("/works/{id}/chapters", publicWork.getId())
                .param("language", "pt-br").with(auth(reader)));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].number").value(2))
                .andExpect(jsonPath("$[1].number").value(10));
    }

    @Test
    void shouldIncludeProcessingChapters_whenOwnerListsOwnPublicWork() throws Exception {
        // Arrange
        published(publicWork, "pt-BR", "1", 1);
        processing(publicWork, "pt-BR", "2");

        // Act / Assert
        mockMvc.perform(get("/works/{id}/chapters", publicWork.getId()).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].status").value("PROCESSING"));
    }

    @Test
    void shouldCountPublishedChaptersPerLanguage_whenLanguagesAreRequested() throws Exception {
        // Arrange
        published(publicWork, "pt-BR", "1", 1);
        published(publicWork, "pt-BR", "2", 1);
        published(publicWork, "en", "1", 1);
        processing(publicWork, "es-419", "1");

        // Act / Assert
        mockMvc.perform(get("/works/{id}/chapters/languages", publicWork.getId()).with(auth(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].language").value("en"))
                .andExpect(jsonPath("$[1].language").value("pt-BR"))
                .andExpect(jsonPath("$[1].chapterCount").value(2));
    }

    @Test
    void shouldReturn404_whenPublicChapterRouteTargetsAPrivateWork() throws Exception {
        // Arrange
        Work privateWork = workRepository.save(Work.createPrivate(
                Slug.of("privada-" + UUID.randomUUID()), "Privada", null, owner.getId()));

        // Act / Assert
        mockMvc.perform(get("/works/{id}/chapters", privateWork.getId()).with(auth(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/my/works/{id}/chapters", privateWork.getId()).with(auth(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/my/works/{id}/chapters", privateWork.getId()).with(auth(reader)))
                .andExpect(status().isForbidden());
    }
}
