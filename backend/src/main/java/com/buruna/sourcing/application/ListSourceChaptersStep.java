package com.buruna.sourcing.application;

import com.buruna.shared.source.ExternalSource;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.SourceChapterInfo;
import com.buruna.shared.source.SourceGroup;
import com.buruna.sourcing.domain.ChapterSelection;
import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.TrackedWork;
import com.buruna.sourcing.persistence.SourceChapterRepository;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lista as versões de capítulo de uma obra acompanhada na fonte, grava as novas e escolhe qual
 * versão de cada número entra ({@link ChapterSelection}). A gravação trava a linha da obra
 * acompanhada: uma versão não é gravada duas vezes (ADR-53).
 */
@Component
class ListSourceChaptersStep {

    private static final Logger log = LoggerFactory.getLogger(ListSourceChaptersStep.class);

    private final ExternalSource source;
    private final TrackedWorkRepository trackedWorkRepository;
    private final SourceChapterRepository sourceChapterRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    ListSourceChaptersStep(ExternalSource source,
                           TrackedWorkRepository trackedWorkRepository,
                           SourceChapterRepository sourceChapterRepository,
                           PlatformTransactionManager transactionManager,
                           Clock clock) {
        this.source = source;
        this.trackedWorkRepository = trackedWorkRepository;
        this.sourceChapterRepository = sourceChapterRepository;
        // transação própria: no profile local isto roda dentro do afterCommit de quem importou
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    void run(TrackedWork tracked) {
        List<SourceChapterInfo> versions;
        try {
            versions = source.listChapters(tracked.getExternalId(), tracked.languageTags());
        } catch (ExternalSourceException e) {
            log.warn("Falha ao listar os capítulos da obra acompanhada {}", tracked.getId(), e);
            transactionTemplate.executeWithoutResult(tx -> trackedWorkRepository.lockById(tracked.getId())
                    .ifPresent(t -> t.recordFailure(e.getMessage(), OffsetDateTime.now(clock))));
            return;
        }
        transactionTemplate.executeWithoutResult(tx -> trackedWorkRepository.lockById(tracked.getId())
                .ifPresent(locked -> store(locked, versions)));
    }

    /**
     * Compara a listagem com o que já se conhece (ADR-49): versão nova entra na escolha, versão
     * que sumiu ganha o aviso "removida na fonte" (o capítulo baixado continua no ar), versão
     * baixada com páginas mais novas ganha o aviso "páginas trocadas", e falha volta para a
     * fila até {@link SourceChapter#MAX_ATTEMPTS} tentativas.
     */
    private void store(TrackedWork tracked, List<SourceChapterInfo> versions) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, SourceChapter> known = sourceChapterRepository.findByTrackedWorkId(tracked.getId()).stream()
                .collect(Collectors.toMap(SourceChapter::getExternalId, Function.identity()));
        // listagem vazia de uma obra que tinha capítulos é mais provável ser erro da fonte do que
        // tudo ter sido removido de uma vez: não marca nada como removido
        if (versions.isEmpty() && !known.isEmpty()) {
            tracked.recordFailure("A fonte não devolveu nenhum capítulo.", now);
            return;
        }
        List<SourceChapter> all = new ArrayList<>(known.values());
        Set<String> listed = new HashSet<>();
        for (SourceChapterInfo version : versions) {
            listed.add(version.externalId());
            SourceChapter existing = known.get(version.externalId());
            if (existing == null) {
                all.add(discover(tracked.getId(), version));
            } else {
                existing.seenAtSource(version.updatedAt(), now);
            }
        }
        for (SourceChapter version : known.values()) {
            if (!listed.contains(version.getExternalId())) {
                version.missingAtSource(now);
            } else if (version.canRetryAutomatically()) {
                version.retry(false);
            }
        }
        ChapterSelection.apply(all, tracked::preferredGroupFor, tracked.ignoredGroupIds());
        sourceChapterRepository.saveAll(all);
        tracked.recordSuccess(now);
        log.info("Obra acompanhada {}: {} versões na fonte, {} novas", tracked.getId(), versions.size(),
                all.size() - known.size());
    }

    private static SourceChapter discover(UUID trackedWorkId, SourceChapterInfo version) {
        return SourceChapter.discover(trackedWorkId, version.externalId(), version.language(), version.number(),
                version.volume(), version.title(),
                version.groups().stream().map(SourceGroup::id).toList(),
                version.groups().stream().map(SourceGroup::name).toList(),
                version.pages(), version.publishedAt(), version.updatedAt());
    }
}
