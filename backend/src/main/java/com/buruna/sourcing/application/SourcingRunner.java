package com.buruna.sourcing.application;

import com.buruna.shared.time.Sleeper;
import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.SourceChapterState;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.TrackedWork;
import com.buruna.sourcing.domain.TrackedWorkStatus;
import com.buruna.sourcing.persistence.SourceChapterRepository;
import com.buruna.sourcing.persistence.SourceLeaseRepository;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Uma rodada do executor de fonte (ADR-48): pega o lease do MangaDex, lista os capítulos das
 * obras recém-importadas e baixa as versões pendentes, até não sobrar nada. Repete a busca no
 * fim, porque um import feito durante a rodada só é visto por ela: a execução disparada por
 * esse import encontra o lease ocupado e espera.
 *
 * <p>Serial de propósito. O limite de taxa da fonte vale por IP, e dois executores ao mesmo
 * tempo o estourariam.
 */
@Service
public class SourcingRunner {

    private static final Logger log = LoggerFactory.getLogger(SourcingRunner.class);
    // renovado a cada capítulo; um capítulo longo (200 páginas nas duas qualidades) passa de 10 min
    private static final Duration LEASE_TTL = Duration.ofMinutes(30);
    private static final Comparator<SourceChapter> READING_ORDER = Comparator
            .comparing((SourceChapter c) -> c.getNumber().map(SourceChapterImporter::parseNumber).orElse(null),
                    Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()))
            .thenComparing(SourceChapter::getLanguage);

    private final TrackedWorkRepository trackedWorkRepository;
    private final SourceChapterRepository sourceChapterRepository;
    private final SourceLeaseRepository leaseRepository;
    private final ListSourceChaptersStep listChapters;
    private final SourceChapterImporter importer;
    private final SourcingDigest digest;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Duration leaseWait;
    private final Duration leasePoll;

    public SourcingRunner(TrackedWorkRepository trackedWorkRepository,
                          SourceChapterRepository sourceChapterRepository,
                          SourceLeaseRepository leaseRepository,
                          ListSourceChaptersStep listChapters,
                          SourceChapterImporter importer,
                          SourcingDigest digest,
                          PlatformTransactionManager transactionManager,
                          Clock clock,
                          Sleeper sleeper,
                          @Value("${app.sourcing.lease-wait:PT15M}") Duration leaseWait,
                          @Value("${app.sourcing.lease-poll:PT30S}") Duration leasePoll) {
        this.trackedWorkRepository = trackedWorkRepository;
        this.sourceChapterRepository = sourceChapterRepository;
        this.leaseRepository = leaseRepository;
        this.listChapters = listChapters;
        this.importer = importer;
        this.digest = digest;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.sleeper = sleeper;
        this.leaseWait = leaseWait;
        this.leasePoll = leasePoll;
    }

    /**
     * Sync (Scheduler, ADR-49): pede a verificação de todas as obras ativas, faz a rodada e, no
     * fim, manda o e-mail diário se for a hora e houver novidade.
     */
    public void sync(String holder) {
        transactionTemplate.executeWithoutResult(tx -> trackedWorkRepository
                .findBySourceAndStatus(SourceName.MANGADEX, TrackedWorkStatus.ACTIVE)
                .forEach(TrackedWork::requestListing));
        run(holder);
        digest.sendIfDue();
    }

    /** {@code holder}: identifica esta execução no lease (id da execução do Job). */
    public void run(String holder) {
        if (!waitForLease(holder)) {
            log.info("Lease do MangaDex ocupado por {}; quem o segura baixa o que estiver pendente", leaseWait);
            return;
        }
        try {
            Set<UUID> listed = new HashSet<>();
            while (drainOnce(holder, listed)) {
                // repete até uma passada não encontrar nada
            }
        } finally {
            transactionTemplate.executeWithoutResult(tx -> leaseRepository.lockBySource(SourceName.MANGADEX)
                    .ifPresent(lease -> lease.release(holder)));
        }
    }

    /** Falso quando não havia nada a fazer. */
    private boolean drainOnce(String holder, Set<UUID> listed) {
        boolean worked = false;
        for (TrackedWork tracked : activeWorks()) {
            // uma tentativa por rodada: listagem que falhou não pode prender a rodada em laço
            if (tracked.needsListing() && listed.add(tracked.getId())) {
                renewLease(holder);
                try {
                    listChapters.run(tracked);
                } catch (RuntimeException e) {
                    log.error("Falha ao listar a obra acompanhada {}", tracked.getId(), e);
                }
                worked = true;
            }
        }
        Map<UUID, TrackedWork> byId = activeWorks().stream()
                .collect(Collectors.toMap(TrackedWork::getId, Function.identity()));
        if (byId.isEmpty()) {
            return worked;
        }
        List<SourceChapter> pending = read(() -> sourceChapterRepository
                .findByTrackedWorkIdInAndState(byId.keySet(), SourceChapterState.PENDING)).stream()
                .sorted(Comparator.comparing(SourceChapter::getTrackedWorkId).thenComparing(READING_ORDER))
                .toList();
        for (SourceChapter version : pending) {
            renewLease(holder);
            importer.run(byId.get(version.getTrackedWorkId()), version);
        }
        return worked || !pending.isEmpty();
    }

    private List<TrackedWork> activeWorks() {
        return read(() -> trackedWorkRepository.findBySourceAndStatus(SourceName.MANGADEX, TrackedWorkStatus.ACTIVE));
    }

    // Toda leitura numa transação nova: no profile local a rodada roda no afterCommit do import,
    // e uma consulta fora de transação usaria o EntityManager que ficou dele, com a obra
    // acompanhada ainda em cache como "nunca listada" (a rodada listaria sem parar).
    private <T> T read(Supplier<T> query) {
        return transactionTemplate.execute(tx -> query.get());
    }

    private boolean waitForLease(String holder) {
        OffsetDateTime deadline = OffsetDateTime.now(clock).plus(leaseWait);
        while (true) {
            if (tryLease(holder)) {
                return true;
            }
            if (!OffsetDateTime.now(clock).isBefore(deadline)) {
                return false;
            }
            sleeper.sleep(leasePoll);
        }
    }

    private boolean tryLease(String holder) {
        Boolean acquired = transactionTemplate.execute(tx -> leaseRepository.lockBySource(SourceName.MANGADEX)
                .map(lease -> lease.tryAcquire(holder, OffsetDateTime.now(clock), LEASE_TTL))
                .orElse(false));
        return Boolean.TRUE.equals(acquired);
    }

    // perder o lease no meio (pausa longa demais) não para a rodada: só avisa no log, porque
    // o risco é outra execução baixar junto por alguns minutos, não perder dado
    private void renewLease(String holder) {
        if (!tryLease(holder)) {
            log.warn("Lease do MangaDex foi tomado por outra execução durante a rodada {}", holder);
        }
    }
}
