package com.buruna.sourcing;

import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.source.ExternalSource;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.SourceChapterInfo;
import com.buruna.shared.source.SourceGroup;
import com.buruna.shared.source.SourcePage;
import com.buruna.shared.storage.StorageClient;
import com.buruna.sourcing.application.SourcingRunner;
import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.SourceChapterState;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.TrackedWork;
import com.buruna.sourcing.persistence.SourceChapterRepository;
import com.buruna.sourcing.persistence.SourceLeaseRepository;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterPage;
import com.buruna.work.persistence.ChapterRepository;
import com.buruna.work.persistence.WorkRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
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
 * Sync das obras acompanhadas (ADR-49): a listagem da fonte muda entre uma rodada e outra, e o
 * relógio é do teste, para o e-mail diário e as datas dos avisos serem previsíveis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class SourcingSyncIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String EXTERNAL_ID = "md-sync-1";
    private static final OffsetDateTime MARCH = OffsetDateTime.parse("2026-03-01T00:00:00Z");
    private static final OffsetDateTime MAY = OffsetDateTime.parse("2026-05-01T00:00:00Z");
    // 9 h em Brasília: o e-mail diário já pode sair
    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-10-17T12:00:00Z"));

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    /** Relógio que só anda quando o teste manda. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            now = start;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

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
    @MockitoBean StorageClient storageClient;
    @MockitoBean EmailService emailService;

    User admin;
    final List<SourceChapterInfo> listing = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        CLOCK.set(Instant.parse("2026-10-17T12:00:00Z"));
        listing.clear();
        transactionTemplate.executeWithoutResult(tx -> {
            sourceChapterRepository.deleteAll();
            trackedWorkRepository.deleteAll();
            chapterRepository.deleteAll();
            workRepository.deleteAll();
            leaseRepository.lockBySource(SourceName.MANGADEX).ifPresent(lease -> {
                if (lease.getHolder() != null) {
                    lease.release(lease.getHolder());
                }
                lease.markDigest(null);
            });
        });
        userRepository.deleteAllInBatch();
        admin = userRepository.save(SourcingIntegrationTest.user("admin@sync.test", "syncAdmin", Role.ADMIN));

        byte[] png = SourcingIntegrationTest.png();
        when(source.fetchWork(EXTERNAL_ID)).thenReturn(SourcingIntegrationTest.details(EXTERNAL_ID));
        when(source.listChapters(eq(EXTERNAL_ID), any())).thenAnswer(invocation -> List.copyOf(listing));
        when(source.fetchPages(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return List.of(new SourcePage("https://node.test/data/" + id + "/1.png", null));
        });
        when(source.download(anyString(), anyLong())).thenAnswer(invocation -> png);
        when(storageClient.upload(any(), anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    static SourceChapterInfo version(String id, String number, String group, OffsetDateTime publishedAt,
                                     OffsetDateTime updatedAt) {
        return new SourceChapterInfo(id, "pt-BR", number, null, null,
                List.of(new SourceGroup(group, "Grupo " + group)), 1, publishedAt, updatedAt);
    }

    String importWork(String preferredGroup) throws Exception {
        String group = preferredGroup == null ? "" : ",\"preferredGroupId\":\"" + preferredGroup + "\",\"preferredGroupName\":\"Grupo " + preferredGroup + "\"";
        String body = mockMvc.perform(post("/admin/sourcing/imports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"" + EXTERNAL_ID + "\",\"languages\":[{\"language\":\"pt-BR\"" + group + "}]}")
                        .with(auth(admin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    SourceChapter versionOf(String externalId) {
        return sourceChapterRepository.findAll().stream()
                .filter(v -> v.getExternalId().equals(externalId)).findFirst().orElseThrow();
    }

    Chapter chapter(UUID id) {
        return transactionTemplate.execute(tx -> {
            Chapter chapter = chapterRepository.findById(id).orElseThrow();
            chapter.getPages().size();
            return chapter;
        });
    }

    TrackedWork tracked() {
        return trackedWorkRepository.findAll().get(0);
    }

    // ── Sync ────────────────────────────────────────────────────────────────

    @Test
    void shouldBringNewAndFlagRemovedAndChanged_whenSyncRuns() throws Exception {
        listing.addAll(List.of(version("c1", "1", "A", MARCH, MARCH), version("c2", "2", "A", MARCH, MARCH)));
        String trackedId = importWork(null);
        UUID chapterOne = versionOf("c1").getChapterId().orElseThrow();
        UUID chapterTwo = versionOf("c2").getChapterId().orElseThrow();

        listing.clear();
        listing.addAll(List.of(version("c1", "1", "A", MARCH, MAY), version("c3", "3", "A", MAY, MAY)));
        runner.sync("sync-1");

        assertThat(versionOf("c3").getState()).isEqualTo(SourceChapterState.READY);
        assertThat(chapterRepository.findById(chapterTwo)).isPresent();
        mockMvc.perform(get("/admin/sourcing/tracked/" + trackedId + "/alerts").with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.type == 'REMOVED_AT_SOURCE')].number").value("2"))
                .andExpect(jsonPath("$[?(@.type == 'PAGES_CHANGED')].number").value("1"));

        mockMvc.perform(post("/admin/sourcing/source-chapters/" + versionOf("c1").getId() + "/redownload").with(auth(admin)))
                .andExpect(status().isAccepted());

        Chapter redownloaded = chapter(chapterOne);
        assertThat(redownloaded.getPages()).extracting(ChapterPage::getObjectName)
                .allMatch(name -> name.startsWith("chapters/" + chapterOne + "/r"));
        assertThat(versionOf("c1").getSourceUpdateDetectedAt()).isNull();
        assertThat(versionOf("c1").getChapterId()).contains(chapterOne);
    }

    @Test
    void shouldSwapPagesKeepingTheChapter_whenAdminUsesThePreferredGroupVersion() throws Exception {
        listing.add(version("c1-b", "1", "B", MARCH, MARCH));
        String trackedId = importWork("A");
        UUID chapterId = versionOf("c1-b").getChapterId().orElseThrow();

        listing.add(version("c1-a", "1", "A", MAY, MAY));
        runner.sync("sync-1");
        mockMvc.perform(get("/admin/sourcing/tracked/" + trackedId + "/alerts").with(auth(admin)))
                .andExpect(jsonPath("$[0].type").value("PREFERRED_AVAILABLE"))
                .andExpect(jsonPath("$[0].detail").value("Grupo B"));

        mockMvc.perform(post("/admin/sourcing/source-chapters/" + versionOf("c1-a").getId() + "/use").with(auth(admin)))
                .andExpect(status().isAccepted());

        assertThat(chapter(chapterId).getScanlationGroup()).contains("Grupo A");
        assertThat(versionOf("c1-a").getChapterId()).contains(chapterId);
        assertThat(versionOf("c1-b").getState()).isEqualTo(SourceChapterState.IGNORED);
        assertThat(versionOf("c1-b").getChapterId()).isEmpty();
    }

    @Test
    void shouldDeleteChaptersAndBringAnotherGroup_whenAdminIgnoresAGroup() throws Exception {
        listing.addAll(List.of(version("c1-a", "1", "A", MAY, MAY), version("c1-b", "1", "B", MARCH, MARCH)));
        String trackedId = importWork(null);
        UUID fromA = versionOf("c1-a").getChapterId().orElseThrow();

        mockMvc.perform(post("/admin/sourcing/tracked/" + trackedId + "/ignored-groups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":\"A\",\"groupName\":\"Grupo A\"}").with(auth(admin)))
                .andExpect(status().isNoContent());

        assertThat(chapterRepository.findById(fromA)).isEmpty();
        UUID fromB = versionOf("c1-b").getChapterId().orElseThrow();
        assertThat(chapter(fromB).getScanlationGroup()).contains("Grupo B");
        mockMvc.perform(get("/admin/sourcing/tracked/" + trackedId + "/groups").with(auth(admin)))
                .andExpect(jsonPath("$[?(@.id == 'A')].ignored").value(true));
    }

    @Test
    void shouldRetryThreeTimesAndThenWaitForTheAdmin_whenDownloadKeepsFailing() throws Exception {
        listing.add(version("c1", "1", "A", MARCH, MARCH));
        when(source.fetchPages("c1")).thenThrow(new ExternalSourceException("nó fora do ar"));
        String trackedId = importWork(null);

        runner.sync("sync-1");
        runner.sync("sync-2");
        runner.sync("sync-3");

        verify(source, times(3)).fetchPages("c1");
        assertThat(versionOf("c1").getState()).isEqualTo(SourceChapterState.FAILED);
        assertThat(versionOf("c1").getAttempts()).isEqualTo(3);
        mockMvc.perform(get("/admin/sourcing/tracked/" + trackedId + "/alerts").with(auth(admin)))
                .andExpect(jsonPath("$[0].type").value("FAILED"));

        doReturn(List.of(new SourcePage("https://node.test/data/c1/1.png", null))).when(source).fetchPages("c1");
        mockMvc.perform(post("/admin/sourcing/source-chapters/" + versionOf("c1").getId() + "/retry").with(auth(admin)))
                .andExpect(status().isAccepted());

        assertThat(versionOf("c1").getState()).isEqualTo(SourceChapterState.READY);
    }

    @Test
    void shouldPauseItself_whenListingFailsEightTimes() throws Exception {
        listing.add(version("c1", "1", "A", MARCH, MARCH));
        String trackedId = importWork(null);
        when(source.listChapters(eq(EXTERNAL_ID), any())).thenThrow(new ExternalSourceException("MangaDex fora do ar"));

        for (int i = 0; i < TrackedWork.FAILURES_BEFORE_PAUSE; i++) {
            runner.sync("sync-" + i);
        }

        assertThat(tracked().isActive()).isFalse();
        mockMvc.perform(get("/admin/sourcing/tracked").with(auth(admin)))
                .andExpect(jsonPath("$[0].status").value("PAUSED"))
                .andExpect(jsonPath("$[0].pausedReason").value(org.hamcrest.Matchers.containsString("8 falhas")));

        mockMvc.perform(post("/admin/sourcing/tracked/" + trackedId + "/resume").with(auth(admin)))
                .andExpect(status().isNoContent());
        assertThat(tracked().isActive()).isTrue();
    }

    @Test
    void shouldEmailAdminsOnceADay_whenThereIsNews() throws Exception {
        listing.add(version("c1", "1", "A", MARCH, MARCH));
        importWork(null);
        clearInvocations(emailService);

        listing.clear();
        runner.sync("sync-1");
        // listagem vazia de obra com capítulos conta como falha da fonte, não como remoção
        assertThat(versionOf("c1").isRemovedAtSource()).isFalse();
        verify(emailService, times(1)).sendSourcingDigest(eq(List.of("admin@sync.test")), anyString(),
                org.mockito.ArgumentMatchers.contains("falha ao verificar"));

        runner.sync("sync-2");
        verify(emailService, times(1)).sendSourcingDigest(anyList(), anyString(), anyString());

        CLOCK.advance(Duration.ofDays(1));
        listing.add(version("c1", "1", "A", MARCH, MARCH));
        clearInvocations(emailService);
        runner.sync("sync-3");
        verify(emailService, never()).sendSourcingDigest(anyList(), anyString(), anyString());
    }

    @Test
    void shouldListAgain_whenAdminAsksToCheckNow() throws Exception {
        listing.add(version("c1", "1", "A", MARCH, MARCH));
        String trackedId = importWork(null);
        listing.add(version("c2", "2", "A", MAY, MAY));

        mockMvc.perform(post("/admin/sourcing/tracked/" + trackedId + "/check").with(auth(admin)))
                .andExpect(status().isAccepted());

        assertThat(versionOf("c2").getState()).isEqualTo(SourceChapterState.READY);
    }
}
