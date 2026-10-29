package com.buruna.work;

import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.storage.StorageClient;
import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterKind;
import com.buruna.work.domain.ChapterNumber;
import com.buruna.work.domain.ChapterPage;
import com.buruna.work.domain.FileHash;
import com.buruna.work.domain.Language;
import com.buruna.work.domain.Slug;
import com.buruna.work.domain.VolumeNumber;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Idiomas no catálogo (ADR-50): selo de cada obra, filtro e a lista para o filtro. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class CatalogLanguageIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @Autowired ChapterRepository chapterRepository;
    @MockitoBean StorageClient storageClient;
    @MockitoBean EmailService emailService;

    User reader;

    @BeforeEach
    void setUp() {
        chapterRepository.deleteAll();
        workRepository.deleteAll();
        userRepository.deleteAllInBatch();
        reader = userRepository.save(ChapterUploadIntegrationTest.user("reader@langs.test", "langsReader", Role.READER));
    }

    RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    Work publicWork(String title) {
        Work work = Work.createPublic(Slug.of(Slug.fromTitle(title).value()), reader.getId());
        work.updateCatalogDetails(title, List.of(), null, WorkFormat.MANGA, null,
                WorkStatusOrigin.ONGOING, WorkStatusSite.INCOMPLETE, null, List.of(), Set.of());
        return workRepository.save(work);
    }

    void publishedChapter(Work work, String language) {
        Chapter chapter = Chapter.register(work.getId(), Language.of(language), ChapterNumber.of(BigDecimal.ONE),
                null, null, null, ChapterKind.PAGES, reader.getId());
        chapter.publishPages(List.of(ChapterPage.of(1, "chapters/x/1.jpg", null, 800, 1200, 1024)),
                OffsetDateTime.parse("2026-10-29T12:00:00Z"));
        chapterRepository.save(chapter);
    }

    @Test
    void shouldShowLanguagesAndFilter_whenWorksHaveChaptersAndLegacyVolumes() throws Exception {
        Work english = publicWork("Obra Em Ingles");
        publishedChapter(english, "en");
        Work both = publicWork("Obra Bilingue");
        publishedChapter(both, "en");
        publishedChapter(both, "pt-BR");
        Work legacy = publicWork("Obra So Volume");
        legacy.addVolume(VolumeNumber.of(1), "volumes/" + UUID.randomUUID() + ".pdf",
                FileHash.of("d41d8cd98f00b204e9800998ecf8427e"), 1024, reader.getId());
        workRepository.save(legacy);
        Work processingOnly = publicWork("Obra Sem Publicado");
        chapterRepository.save(Chapter.register(processingOnly.getId(), Language.of("es"), ChapterNumber.of(BigDecimal.ONE),
                null, null, null, ChapterKind.PAGES, reader.getId()));

        mockMvc.perform(get("/works").param("sort", "title").with(auth(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == 'Obra Bilingue')].languages[*]", containsInAnyOrder("en", "pt-BR")))
                .andExpect(jsonPath("$.content[?(@.title == 'Obra So Volume')].languages[*]", contains("pt-BR")));

        mockMvc.perform(get("/works").param("language", "pt-br").with(auth(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Obra Bilingue", "Obra So Volume")));

        mockMvc.perform(get("/works").param("language", "en").with(auth(reader)))
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Obra Em Ingles", "Obra Bilingue")));

        mockMvc.perform(get("/works/languages").with(auth(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("en", "pt-BR")));
    }

    @Test
    void shouldRejectInvalidLanguage_whenFiltering() throws Exception {
        mockMvc.perform(get("/works").param("language", "não é idioma").with(auth(reader)))
                .andExpect(status().isBadRequest());
    }
}
