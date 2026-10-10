package com.buruna.work.application;

import com.buruna.shared.exception.StorageObjectNotFoundException;
import com.buruna.shared.media.ArchiveLimits;
import com.buruna.shared.media.ComicArchiveExtractor;
import com.buruna.shared.media.InvalidArchiveException;
import com.buruna.shared.storage.StorageClient;
import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterObjectName;
import com.buruna.work.domain.ChapterPage;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.persistence.ChapterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Extrai as páginas do arquivo enviado (CBZ) e publica o capítulo (ADR-48). Roda no Cloud Run
 * Job, ou na própria requisição no profile local.
 *
 * <p>Sem transação longa: baixar e gravar páginas leva minutos, e segurar uma conexão do pool
 * nesse tempo não faz sentido. Só a leitura inicial e a publicação final são transações.
 *
 * <p>Idempotente: capítulo que não está mais em processamento é ignorado, e as páginas têm nome
 * determinístico, então uma reexecução (retry do Job) sobrescreve os mesmos objetos.
 */
@Service
public class ProcessChapterSourceUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessChapterSourceUseCase.class);
    private static final long MB = 1024L * 1024;

    private final ChapterRepository chapterRepository;
    private final StorageClient storageClient;
    private final ComicArchiveExtractor extractor;
    private final FailChapterIngestUseCase failChapterIngest;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final ArchiveLimits limits;

    public ProcessChapterSourceUseCase(ChapterRepository chapterRepository,
                                       StorageClient storageClient,
                                       ComicArchiveExtractor extractor,
                                       FailChapterIngestUseCase failChapterIngest,
                                       PlatformTransactionManager transactionManager,
                                       Clock clock,
                                       @Value("${app.ingest.max-pages}") int maxPages,
                                       @Value("${app.ingest.max-total-mb}") long maxTotalMb,
                                       @Value("${app.ingest.max-page-mb}") long maxPageMb) {
        this.chapterRepository = chapterRepository;
        this.storageClient = storageClient;
        this.extractor = extractor;
        this.failChapterIngest = failChapterIngest;
        // transação própria sempre: no profile local isto roda dentro do afterCommit de quem fez
        // o upload, e uma transação REQUIRED ali entraria na que acabou de terminar, sem commit
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.limits = new ArchiveLimits(maxPages, maxTotalMb * MB, maxPageMb * MB);
    }

    public void handle(UUID chapterId) {
        Optional<String> source = transactionTemplate.execute(tx -> chapterRepository.findById(chapterId)
                .filter(chapter -> chapter.getStatus() == ChapterStatus.PROCESSING)
                .flatMap(Chapter::getSourceObjectName));
        if (source == null || source.isEmpty()) {
            log.info("Capítulo {} não está aguardando extração; nada a fazer", chapterId);
            return;
        }

        List<ChapterPage> pages = new ArrayList<>();
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("chapter-" + chapterId, ".cbz");
            try (InputStream in = storageClient.openRead(source.get())) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            extractor.extractCbz(tempFile, limits, page -> {
                String objectName = ChapterObjectName.page(chapterId, page.position(), page.extension());
                storageClient.upload(new ByteArrayInputStream(page.content()), objectName,
                        page.contentType(), page.content().length);
                pages.add(ChapterPage.of(page.position(), objectName, null,
                        page.width(), page.height(), page.content().length));
            });
            publish(chapterId, pages);
        } catch (InvalidArchiveException e) {
            log.info("Capítulo {} recusado: {}", chapterId, e.getMessage());
            deleteQuietly(objectNamesOf(pages));
            failChapterIngest.discardingSource(chapterId, e.getMessage()).ifPresent(this::deleteQuietly);
        } catch (StorageObjectNotFoundException e) {
            log.warn("Arquivo do capítulo {} sumiu do storage: {}", chapterId, source.get());
            deleteQuietly(objectNamesOf(pages));
            failChapterIngest.discardingSource(chapterId, "O arquivo enviado não foi encontrado. Envie de novo.");
        } catch (IOException | RuntimeException e) {
            log.error("Falha ao processar o capítulo {}", chapterId, e);
            deleteQuietly(objectNamesOf(pages));
            failChapterIngest.keepingSource(chapterId, "Falha temporária ao processar o capítulo. Tente de novo.");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Não foi possível apagar o temporário {}", tempFile, e);
                }
            }
        }
    }

    private record Publication(boolean published, Optional<String> discardedSource) {
        static final Publication SKIPPED = new Publication(false, Optional.empty());
    }

    private void publish(UUID chapterId, List<ChapterPage> pages) {
        Publication publication = transactionTemplate.execute(tx -> chapterRepository.findWithPagesById(chapterId)
                .filter(chapter -> chapter.getStatus() == ChapterStatus.PROCESSING)
                .map(chapter -> {
                    chapter.publishPages(pages, OffsetDateTime.now(clock));
                    return new Publication(true, chapter.discardSource());
                })
                .orElse(Publication.SKIPPED));

        if (publication != null && publication.published()) {
            publication.discardedSource().ifPresent(this::deleteQuietly);
            return;
        }
        // o capítulo foi apagado ou resolvido enquanto as páginas eram extraídas. Se outra
        // execução já o publicou, as páginas dela têm os mesmos nomes destas e ficam.
        boolean publishedElsewhere = chapterRepository.findById(chapterId)
                .map(chapter -> chapter.getStatus() == ChapterStatus.PUBLISHED)
                .orElse(false);
        if (!publishedElsewhere) {
            deleteQuietly(objectNamesOf(pages));
        }
    }

    private static List<String> objectNamesOf(List<ChapterPage> pages) {
        return pages.stream().map(ChapterPage::getObjectName).toList();
    }

    private void deleteQuietly(List<String> objectNames) {
        objectNames.forEach(this::deleteQuietly);
    }

    // best-effort, como no delete de volume: um objeto que fica para trás não pode desfazer o
    // estado do capítulo, que já está gravado
    private void deleteQuietly(String objectName) {
        try {
            storageClient.delete(objectName);
        } catch (RuntimeException e) {
            log.warn("Não foi possível apagar {} do storage", objectName, e);
        }
    }
}
