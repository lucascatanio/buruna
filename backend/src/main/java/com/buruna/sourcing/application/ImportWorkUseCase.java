package com.buruna.sourcing.application;

import com.buruna.shared.media.ImageInspector;
import com.buruna.shared.source.ExternalSource;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.ExternalWorkNotFoundException;
import com.buruna.shared.source.SourceWorkDetails;
import com.buruna.sourcing.domain.SourceChapterState;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.SourceUnavailableException;
import com.buruna.sourcing.domain.SourceWorkNotFoundException;
import com.buruna.sourcing.domain.TrackedLanguage;
import com.buruna.sourcing.domain.TrackedWork;
import com.buruna.sourcing.domain.WorkAlreadyTrackedException;
import com.buruna.sourcing.persistence.SourceLeaseRepository;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import com.buruna.work.application.CreateImportedWorkUseCase;
import com.buruna.work.application.FindPublicWorkUseCase;
import com.buruna.work.application.WorkInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Começa a acompanhar uma obra do MangaDex (ADR-46, ADR-47): cria a obra no Burūna, ou vincula
 * a uma que já existe, e pede uma rodada do executor, que lista e baixa os capítulos.
 *
 * <p>As chamadas à fonte (metadados e capa) ficam fora da transação. A gravação trava a linha
 * do lease da fonte: dois imports da mesma obra ao mesmo tempo não criam dois acompanhamentos
 * (ADR-53), e as tags criadas por um são vistas pelo outro.
 */
@Service
public class ImportWorkUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportWorkUseCase.class);
    private static final long MAX_COVER_BYTES = 10L * 1024 * 1024;
    private static final Set<ImageInspector.ImageType> COVER_TYPES = EnumSet.of(
            ImageInspector.ImageType.JPEG, ImageInspector.ImageType.PNG, ImageInspector.ImageType.WEBP);

    private final ExternalSource source;
    private final TrackedWorkRepository trackedWorkRepository;
    private final SourceLeaseRepository leaseRepository;
    private final CreateImportedWorkUseCase createImportedWork;
    private final FindPublicWorkUseCase findPublicWork;
    private final SourcingTrigger trigger;
    private final TransactionTemplate transactionTemplate;

    public ImportWorkUseCase(ExternalSource source,
                             TrackedWorkRepository trackedWorkRepository,
                             SourceLeaseRepository leaseRepository,
                             CreateImportedWorkUseCase createImportedWork,
                             FindPublicWorkUseCase findPublicWork,
                             SourcingTrigger trigger,
                             PlatformTransactionManager transactionManager) {
        this.source = source;
        this.trackedWorkRepository = trackedWorkRepository;
        this.leaseRepository = leaseRepository;
        this.createImportedWork = createImportedWork;
        this.findPublicWork = findPublicWork;
        this.trigger = trigger;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TrackedWorkResponse handle(ImportWorkRequest request, UUID actorId) {
        List<TrackedLanguage> languages = request.languages().stream()
                .map(choice -> TrackedLanguage.of(choice.language(), choice.preferredGroupId(), choice.preferredGroupName()))
                .toList();
        SourceWorkDetails details = fetchDetails(request.externalId());
        String cover = request.linkWorkId() == null ? downloadCover(details) : null;

        return transactionTemplate.execute(tx -> {
            leaseRepository.lockBySource(SourceName.MANGADEX).orElseThrow();
            if (trackedWorkRepository.existsBySourceAndExternalId(SourceName.MANGADEX, details.externalId())) {
                throw new WorkAlreadyTrackedException("Esta obra do MangaDex já está sendo acompanhada.");
            }
            WorkInfo work = request.linkWorkId() == null
                    ? createImportedWork.handle(WorkMetadataMapper.toCommand(details, cover), actorId)
                    : linkedWork(request.linkWorkId());
            TrackedWork tracked = trackedWorkRepository.save(TrackedWork.track(
                    work.id(), SourceName.MANGADEX, details.externalId(), languages, actorId));
            trigger.requestRun();
            return TrackedWorkResponse.from(tracked, work.title(), work.slug(), new EnumMap<>(SourceChapterState.class));
        });
    }

    private WorkInfo linkedWork(UUID workId) {
        WorkInfo work = findPublicWork.getPublicWorkInfo(workId);
        if (trackedWorkRepository.existsByWorkId(workId)) {
            throw new WorkAlreadyTrackedException("Esta obra do Burūna já está vinculada a uma obra do MangaDex.");
        }
        return work;
    }

    private SourceWorkDetails fetchDetails(String externalId) {
        try {
            return source.fetchWork(externalId);
        } catch (ExternalWorkNotFoundException e) {
            throw new SourceWorkNotFoundException(externalId);
        } catch (ExternalSourceException e) {
            log.warn("Falha ao buscar a obra {} na fonte", externalId, e);
            throw new SourceUnavailableException();
        }
    }

    // capa é opcional: sem ela a obra entra do mesmo jeito, e o ADM pode pôr uma depois
    private String downloadCover(SourceWorkDetails details) {
        if (details.coverUrl() == null) {
            return null;
        }
        try {
            byte[] content = source.download(details.coverUrl(), MAX_COVER_BYTES);
            // a capa da obra aceita JPEG, PNG e WebP (StorageUploadHelper)
            return ImageInspector.detectType(content)
                    .filter(COVER_TYPES::contains)
                    .map(type -> "data:" + type.contentType() + ";base64," + Base64.getEncoder().encodeToString(content))
                    .orElse(null);
        } catch (ExternalSourceException e) {
            log.warn("Capa da obra {} não baixou; segue sem capa", details.externalId(), e);
            return null;
        }
    }
}
