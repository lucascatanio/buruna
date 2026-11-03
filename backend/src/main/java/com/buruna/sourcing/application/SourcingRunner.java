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
 * Uma rodada do executor de uma fonte (ADR-48, ADR-54): pega o lease dela, lista os capítulos das
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
                          // curto: quem segura o lease repete a busca no fim e pega o que chegar;
                          // esperar mais só deixa uma execução de 4 GiB parada
                          @Value("${app.sourcing.lease-wait:PT2M}") Duration leaseWait,
                          @Value("${app.sourcing.lease-poll:PT15S}") Duration leasePoll) {
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
     * Sync (Scheduler, ADR-49) das obras do MangaDex: pede a verificação de todas as ativas, faz a
     * rodada e, no fim, manda o e-mail diário se for a hora e houver novidade. O sync dos sites do
     * manga-downloader fica para outra ADR (ADR-54).
     */
    public void sync(String holder) {
        transactionTemplate.executeWithoutResult(tx -> trackedWorkRepository
                .findBySourceAndStatus(SourceName.MANGADEX, TrackedWorkStatus.ACTIVE)
                .forEach(TrackedWork::requestListing));
        run(holder, SourceName.MANGADEX);
        digest.sendIfDue();
    }

    /** {@code holder}: identifica esta execução no lease (id da execução do Job). */
    public void run(String holder, SourceName source) {
        if (!waitForLease(holder, source)) {
            log.info("Lease de {} ocupado por {}; quem o segura baixa o que estiver pendente", source, leaseWait);
            return;
        }
        try {
            Set<UUID> listed = new HashSet<>();
            Set<UUID> deferred = new HashSet<>();
            while (drainOnce(holder, source, listed, deferred)) {
                // repete até uma passada não encontrar nada
            }
        } finally {
            release(holder, source);
        }
    }

    /**
     * Falso quando não havia nada a fazer. {@code deferred}: versões que esperam o lease do MangaDex
     * (ADR-54); ficam PENDING para a próxima rodada, sem prender esta.
     */
    private boolean drainOnce(String holder, SourceName source, Set<UUID> listed, Set<UUID> deferred) {
        boolean worked = false;
        for (TrackedWork tracked : activeWorks(source)) {
            // uma tentativa por rodada: listagem que falhou não pode prender a rodada em laço
            if (tracked.needsListing() && listed.add(tracked.getId())) {
                renewLease(holder, source);
                worked |= withMangaDexLease(tracked, holder, () -> {
                    try {
                        listChapters.run(tracked);
                    } catch (RuntimeException e) {
                        log.error("Falha ao listar a obra acompanhada {}", tracked.getId(), e);
                    }
                });
            }
        }
        Map<UUID, TrackedWork> byId = activeWorks(source).stream()
                .collect(Collectors.toMap(TrackedWork::getId, Function.identity()));
        if (byId.isEmpty()) {
            return worked;
        }
        List<SourceChapter> pending = read(() -> sourceChapterRepository
                .findByTrackedWorkIdInAndState(byId.keySet(), SourceChapterState.PENDING)).stream()
                .filter(version -> !deferred.contains(version.getId()))
                .sorted(Comparator.comparing(SourceChapter::getTrackedWorkId).thenComparing(READING_ORDER))
                .toList();
        for (SourceChapter version : pending) {
            renewLease(holder, source);
            TrackedWork tracked = byId.get(version.getTrackedWorkId());
            if (withMangaDexLease(tracked, holder, () -> importer.run(tracked, version))) {
                worked = true;
            } else {
                deferred.add(version.getId());
            }
        }
        return worked;
    }

    /**
     * Série do mangadex.org pelo manga-downloader: só roda se o lease do MangaDex estiver livre, e
     * o devolve logo depois. Ocupado (o sync das obras antigas pode levar horas), não espera:
     * esperar travaria todos os sites por causa de um (ADR-54). Falso quando não rodou.
     */
    private boolean withMangaDexLease(TrackedWork tracked, String holder, Runnable action) {
        if (!tracked.sharesMangaDexLease()) {
            action.run();
            return true;
        }
        if (!tryLease(holder, SourceName.MANGADEX)) {
            log.info("Lease do MangaDex ocupado; obra acompanhada {} fica para a próxima rodada", tracked.getId());
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            release(holder, SourceName.MANGADEX);
        }
    }

    private List<TrackedWork> activeWorks(SourceName source) {
        return read(() -> trackedWorkRepository.findBySourceAndStatus(source, TrackedWorkStatus.ACTIVE));
    }

    // Toda leitura numa transação nova: no profile local a rodada roda no afterCommit do import,
    // e uma consulta fora de transação usaria o EntityManager que ficou dele, com a obra
    // acompanhada ainda em cache como "nunca listada" (a rodada listaria sem parar).
    private <T> T read(Supplier<T> query) {
        return transactionTemplate.execute(tx -> query.get());
    }

    private boolean waitForLease(String holder, SourceName source) {
        OffsetDateTime deadline = OffsetDateTime.now(clock).plus(leaseWait);
        while (true) {
            if (tryLease(holder, source)) {
                return true;
            }
            if (!OffsetDateTime.now(clock).isBefore(deadline)) {
                return false;
            }
            sleeper.sleep(leasePoll);
        }
    }

    private boolean tryLease(String holder, SourceName source) {
        Boolean acquired = transactionTemplate.execute(tx -> leaseRepository.lockBySource(source)
                .map(lease -> lease.tryAcquire(holder, OffsetDateTime.now(clock), LEASE_TTL))
                .orElse(false));
        return Boolean.TRUE.equals(acquired);
    }

    private void release(String holder, SourceName source) {
        transactionTemplate.executeWithoutResult(tx -> leaseRepository.lockBySource(source)
                .ifPresent(lease -> lease.release(holder)));
    }

    // perder o lease no meio (pausa longa demais) não para a rodada: só avisa no log, porque
    // o risco é outra execução baixar junto por alguns minutos, não perder dado
    private void renewLease(String holder, SourceName source) {
        if (!tryLease(holder, source)) {
            log.warn("Lease de {} foi tomado por outra execução durante a rodada {}", source, holder);
        }
    }
}
