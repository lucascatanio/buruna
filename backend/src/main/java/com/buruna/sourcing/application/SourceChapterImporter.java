package com.buruna.sourcing.application;

import com.buruna.shared.exception.DomainException;
import com.buruna.shared.media.ImageInspector;
import com.buruna.shared.media.ImageInspector.InspectedImage;
import com.buruna.shared.media.InvalidArchiveException;
import com.buruna.shared.source.ExternalSource;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.SourcePage;
import com.buruna.shared.storage.StorageClient;
import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.TrackedWork;
import com.buruna.sourcing.persistence.SourceChapterRepository;
import com.buruna.work.application.ImportedPage;
import com.buruna.work.application.PublishImportedChapterUseCase;
import com.buruna.work.application.RegisterChapterCommand;
import com.buruna.work.application.RegisterChapterUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Transforma uma versão escolhida (PENDING) em capítulo do Burūna: registra o capítulo em
 * processamento, baixa cada página nas duas qualidades que a fonte oferece, grava no storage e
 * publica. Guarda os bytes originais (ADR-48): só GIF, BMP e TIFF são convertidos, como no upload.
 *
 * <p>Sem transação longa: cada gravação no banco é curta. Se o processo morrer no meio, a versão
 * continua PENDING com o capítulo já registrado, e a próxima rodada retoma do mesmo capítulo:
 * as páginas têm nome determinístico e são sobrescritas.
 */
@Component
class SourceChapterImporter {

    private static final Logger log = LoggerFactory.getLogger(SourceChapterImporter.class);
    private static final long MB = 1024L * 1024;
    private static final int MAX_LABEL_LENGTH = 100;
    // chapters.scanlation_group
    private static final int MAX_GROUP_LENGTH = 255;
    private static final int MAX_NUMBER_SCALE = 2;
    private static final int MAX_NUMBER_INTEGER_DIGITS = 8;

    private final ExternalSource source;
    private final StorageClient storageClient;
    private final SourceChapterRepository sourceChapterRepository;
    private final RegisterChapterUseCase registerChapter;
    private final PublishImportedChapterUseCase publishChapter;
    private final TransactionTemplate transactionTemplate;
    private final long maxPageBytes;

    SourceChapterImporter(ExternalSource source,
                          StorageClient storageClient,
                          SourceChapterRepository sourceChapterRepository,
                          RegisterChapterUseCase registerChapter,
                          PublishImportedChapterUseCase publishChapter,
                          PlatformTransactionManager transactionManager,
                          @Value("${app.ingest.max-page-mb}") long maxPageMb) {
        this.source = source;
        this.storageClient = storageClient;
        this.sourceChapterRepository = sourceChapterRepository;
        this.registerChapter = registerChapter;
        this.publishChapter = publishChapter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.maxPageBytes = maxPageMb * MB;
    }

    void run(TrackedWork tracked, SourceChapter version) {
        UUID chapterId;
        try {
            chapterId = version.getChapterId().orElseGet(() -> register(tracked, version));
        } catch (DomainException e) {
            // número já ocupado por um capítulo enviado à mão, ou número/rótulo inválido
            log.info("Versão {} não registrada: {}", version.getExternalId(), e.getMessage());
            save(version, v -> v.fail(e.getMessage()));
            return;
        } catch (RuntimeException e) {
            // sem isto a versão ficaria PENDING e travaria todas as rodadas no mesmo ponto
            log.error("Falha inesperada ao registrar a versão {}", version.getExternalId(), e);
            save(version, v -> v.fail("Falha ao registrar o capítulo: " + e.getMessage()));
            return;
        }

        List<ImportedPage> pages = new ArrayList<>();
        try {
            download(version, chapterId, pages);
        } catch (RuntimeException e) {
            log.warn("Falha ao baixar a versão {} (capítulo {})", version.getExternalId(), chapterId, e);
            deleteQuietly(pages);
            publishChapter.discard(chapterId);
            save(version, v -> v.fail("Falha ao baixar as páginas: " + e.getMessage()));
            return;
        }

        if (publishChapter.handle(chapterId, pages)) {
            save(version, SourceChapter::markReady);
            log.info("Versão {} publicada como capítulo {} ({} páginas)", version.getExternalId(), chapterId, pages.size());
        } else {
            // o ADM apagou o capítulo durante o download
            deleteQuietly(pages);
            save(version, v -> v.fail("O capítulo foi apagado durante o download."));
        }
    }

    private UUID register(TrackedWork tracked, SourceChapter version) {
        BigDecimal number = version.getNumber().map(SourceChapterImporter::parseNumber).orElse(null);
        String label = number != null ? null : labelOf(version);
        // oneshot usa o título como rótulo; repetir como título mostraria "Especial · Especial"
        String title = label != null && label.equals(version.getTitle()) ? null : version.getTitle();
        String groups = version.getGroupNames() == null || version.getGroupNames().length() <= MAX_GROUP_LENGTH
                ? version.getGroupNames()
                : version.getGroupNames().substring(0, MAX_GROUP_LENGTH);
        // transação própria: no profile local isto roda dentro do afterCommit de quem importou,
        // e o REQUIRED do use case entraria na transação que acabou de terminar
        UUID chapterId = transactionTemplate.execute(tx -> registerChapter.handle(RegisterChapterCommand.importedPages(
                tracked.getWorkId(), version.getLanguage(), number, label, title,
                groups, tracked.getCreatedBy())).id());
        save(version, v -> v.attachChapter(chapterId));
        return chapterId;
    }

    private void download(SourceChapter version, UUID chapterId, List<ImportedPage> pages) {
        List<SourcePage> sourcePages = source.fetchPages(version.getExternalId());
        for (int i = 0; i < sourcePages.size(); i++) {
            int position = i + 1;
            SourcePage page = sourcePages.get(i);
            InspectedImage original = ImageInspector.inspect(source.download(page.url(), maxPageBytes), page.url());
            String objectName = PublishImportedChapterUseCase.pageObjectName(chapterId, position, original.extension());
            upload(objectName, original);
            String dataSaverName = page.dataSaverUrl() == null ? null : dataSaver(chapterId, position, page.dataSaverUrl());
            pages.add(new ImportedPage(position, objectName, dataSaverName,
                    original.width(), original.height(), original.content().length));
        }
        if (pages.isEmpty()) {
            throw new ExternalSourceException("A fonte não devolveu nenhuma página");
        }
    }

    // a versão leve é um extra: se falhar, a página fica só com a original
    private String dataSaver(UUID chapterId, int position, String url) {
        try {
            InspectedImage image = ImageInspector.inspect(source.download(url, maxPageBytes), url);
            String objectName = PublishImportedChapterUseCase.dataSaverObjectName(chapterId, position, image.extension());
            upload(objectName, image);
            return objectName;
        } catch (ExternalSourceException | InvalidArchiveException e) {
            log.info("Página {} do capítulo {} sem versão leve: {}", position, chapterId, e.getMessage());
            return null;
        }
    }

    private void upload(String objectName, InspectedImage image) {
        storageClient.upload(new ByteArrayInputStream(image.content()), objectName,
                image.contentType(), image.content().length);
    }

    /** "10.5" vira número; o que não cabe em NUMERIC(10,2) ("10.125", "10a") vira rótulo. */
    static BigDecimal parseNumber(String raw) {
        try {
            BigDecimal number = new BigDecimal(raw.trim()).stripTrailingZeros();
            if (number.signum() < 0 || number.scale() > MAX_NUMBER_SCALE
                    || number.precision() - number.scale() > MAX_NUMBER_INTEGER_DIGITS) {
                return null;
            }
            return number.scale() < 0 ? number.setScale(0) : number;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String labelOf(SourceChapter version) {
        String label = version.getNumber().map(raw -> "Cap. " + raw)
                .orElseGet(() -> version.getTitle() != null ? version.getTitle() : "Oneshot");
        return label.length() > MAX_LABEL_LENGTH ? label.substring(0, MAX_LABEL_LENGTH) : label;
    }

    private void save(SourceChapter version, Consumer<SourceChapter> change) {
        transactionTemplate.executeWithoutResult(tx -> sourceChapterRepository.findById(version.getId())
                .ifPresent(fresh -> {
                    change.accept(fresh);
                    change.accept(version);
                }));
    }

    private void deleteQuietly(List<ImportedPage> pages) {
        for (ImportedPage page : pages) {
            deleteQuietly(page.objectName());
            if (page.dataSaverObjectName() != null) {
                deleteQuietly(page.dataSaverObjectName());
            }
        }
    }

    private void deleteQuietly(String objectName) {
        try {
            storageClient.delete(objectName);
        } catch (RuntimeException e) {
            log.warn("Não foi possível apagar {} do storage", objectName, e);
        }
    }
}
