package com.buruna.work;

import com.buruna.identity.domain.Email;
import com.buruna.identity.domain.Quota;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.Username;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.shared.storage.StorageClient;
import com.buruna.work.application.ChapterResponse;
import com.buruna.work.application.RegisterChapterCommand;
import com.buruna.work.application.RegisterChapterUseCase;
import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterKind;
import com.buruna.work.domain.ChapterPage;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.domain.DuplicateChapterException;
import com.buruna.work.domain.Slug;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.ChapterRepository;
import com.buruna.work.persistence.WorkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registro de capítulo contra Postgres real: a regra de número único vive na aplicação, sem
 * UNIQUE no banco (ADR-53), então só um teste com transações concorrentes de verdade mostra
 * que o lock na obra segura a corrida.
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Testcontainers
class RegisterChapterIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired RegisterChapterUseCase registerChapter;
    @Autowired ChapterRepository chapterRepository;
    @Autowired WorkRepository workRepository;
    @Autowired UserRepository userRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @MockitoBean StorageClient storageClient;

    User uploader;
    Work work;

    @BeforeEach
    void setUp() {
        chapterRepository.deleteAll();
        workRepository.deleteAll();
        userRepository.deleteAllInBatch();

        uploader = userRepository.save(User.register(
                Email.of("uploader@chapter.test"), Username.of("chapterUploader"),
                "$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC", "test",
                Quota.of(BigDecimal.ONE)));
        work = workRepository.save(Work.createPrivate(
                Slug.fromTitle("Obra " + UUID.randomUUID()), "Obra", null, uploader.getId()));
    }

    RegisterChapterCommand chapter(String language, String number) {
        return new RegisterChapterCommand(work.getId(), language, number == null ? null : new BigDecimal(number),
                number == null ? "Extra" : null, null, null, ChapterKind.PAGES, uploader.getId());
    }

    @Test
    void shouldPersistProcessingChapter_whenNumberIsFree() {
        // Act
        ChapterResponse response = registerChapter.handle(chapter("pt-br", "1"));

        // Assert
        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.language()).isEqualTo("pt-BR");
        Chapter saved = chapterRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getWorkId()).isEqualTo(work.getId());
        assertThat(saved.getStatus()).isEqualTo(ChapterStatus.PROCESSING);
    }

    @Test
    void shouldThrowDuplicateChapter_whenSameNumberAndLanguageExist() {
        // Arrange
        registerChapter.handle(chapter("pt-BR", "1"));

        // Act / Assert: 1.0 é o mesmo capítulo que 1
        assertThatThrownBy(() -> registerChapter.handle(chapter("pt-BR", "1.0")))
                .isInstanceOf(DuplicateChapterException.class);
    }

    @Test
    void shouldAcceptSameNumber_whenLanguageDiffers() {
        // Arrange
        registerChapter.handle(chapter("pt-BR", "1"));

        // Act
        ChapterResponse english = registerChapter.handle(chapter("en", "1"));

        // Assert
        assertThat(english.language()).isEqualTo("en");
        assertThat(chapterRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldFreeNumber_whenPreviousAttemptFailed() {
        // Arrange
        ChapterResponse first = registerChapter.handle(chapter("pt-BR", "1"));
        transactionTemplate.executeWithoutResult(tx -> {
            Chapter chapter = chapterRepository.findById(first.id()).orElseThrow();
            chapter.fail("CBZ corrompido");
        });

        // Act
        ChapterResponse retry = registerChapter.handle(chapter("pt-BR", "1"));

        // Assert
        assertThat(retry.id()).isNotEqualTo(first.id());
        assertThat(retry.status()).isEqualTo("PROCESSING");
    }

    @Test
    void shouldAllowSeveralChaptersWithoutNumber_whenTheyAreExtras() {
        // Arrange
        registerChapter.handle(chapter("pt-BR", null));

        // Act
        registerChapter.handle(chapter("pt-BR", null));

        // Assert
        assertThat(chapterRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldThrowWorkNotFound_whenWorkDoesNotExist() {
        // Arrange
        RegisterChapterCommand command = new RegisterChapterCommand(UUID.randomUUID(), "pt-BR",
                BigDecimal.ONE, null, null, null, ChapterKind.PAGES, uploader.getId());

        // Act / Assert
        assertThatThrownBy(() -> registerChapter.handle(command)).isInstanceOf(WorkNotFoundException.class);
    }

    @Test
    void shouldKeepPagesInOrder_whenPublishedChapterIsReloaded() {
        // Arrange
        ChapterResponse registered = registerChapter.handle(chapter("pt-BR", "1"));

        // Act
        transactionTemplate.executeWithoutResult(tx -> {
            Chapter chapter = chapterRepository.findById(registered.id()).orElseThrow();
            chapter.publishPages(List.of(
                    ChapterPage.of(2, "chapters/x/2.jpg", "chapters/x/2-ds.jpg", 800, 1200, 2048),
                    ChapterPage.of(1, "chapters/x/1.jpg", null, 800, 1200, 1024)),
                    OffsetDateTime.parse("2026-10-09T12:00:00Z"));
        });

        // Assert
        transactionTemplate.executeWithoutResult(tx -> {
            Chapter reloaded = chapterRepository.findById(registered.id()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(ChapterStatus.PUBLISHED);
            assertThat(reloaded.getPages()).extracting(ChapterPage::getPosition).containsExactly(1, 2);
            assertThat(reloaded.getPages().get(1).getDataSaverObjectName()).contains("chapters/x/2-ds.jpg");
            assertThat(reloaded.getPages().get(0).getDataSaverObjectName()).isEmpty();
        });
    }

    @Test
    void shouldAcceptOnlyOne_whenTwoRegistrationsOfTheSameNumberRace() throws Exception {
        // Arrange
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    registerChapter.handle(chapter("pt-BR", "7"));
                    return true;
                } catch (DuplicateChapterException e) {
                    return false;
                }
            }));
        }

        // Act
        start.countDown();
        List<Boolean> outcomes = new ArrayList<>();
        for (Future<Boolean> result : results) {
            outcomes.add(result.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        // Assert
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(chapterRepository.count()).isEqualTo(1);
    }
}
