package com.buruna.sourcing.application;

import com.buruna.identity.application.ListActiveAdminEmailsUseCase;
import com.buruna.shared.notification.EmailService;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.TrackedWork;
import com.buruna.sourcing.persistence.SourceChapterRepository;
import com.buruna.sourcing.persistence.SourceLeaseRepository;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import com.buruna.work.application.FindPublicWorkUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * E-mail diário do sync para os ADMs (ADR-49). Sai no primeiro sync depois das 8 h (Brasília),
 * só se houver novidade desde o último: versão que falhou de vez, capítulo removido na fonte,
 * páginas trocadas, versão do grupo preferido disponível, obra pausada sozinha ou com falha ao
 * verificar. Sem novidade, só registra o dia, para o próximo resumo começar dali.
 */
@Component
class SourcingDigest {

    private static final Logger log = LoggerFactory.getLogger(SourcingDigest.class);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalTime SEND_AFTER = LocalTime.of(8, 0);
    private static final int MAX_LINES = 50;
    private static final Map<SourceAlert.Type, String> LABELS = Map.of(
            SourceAlert.Type.FAILED, "falhou ao baixar",
            SourceAlert.Type.REMOVED_AT_SOURCE, "removido no MangaDex (continua no ar aqui)",
            SourceAlert.Type.PAGES_CHANGED, "páginas trocadas no MangaDex",
            SourceAlert.Type.PREFERRED_AVAILABLE, "versão do grupo preferido disponível");

    private final SourceLeaseRepository leaseRepository;
    private final TrackedWorkRepository trackedWorkRepository;
    private final SourceChapterRepository sourceChapterRepository;
    private final FindPublicWorkUseCase findPublicWork;
    private final ListActiveAdminEmailsUseCase adminEmails;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final String frontendUrl;

    SourcingDigest(SourceLeaseRepository leaseRepository,
                   TrackedWorkRepository trackedWorkRepository,
                   SourceChapterRepository sourceChapterRepository,
                   FindPublicWorkUseCase findPublicWork,
                   ListActiveAdminEmailsUseCase adminEmails,
                   EmailService emailService,
                   PlatformTransactionManager transactionManager,
                   Clock clock,
                   @Value("${app.frontend-url}") String frontendUrl) {
        this.leaseRepository = leaseRepository;
        this.trackedWorkRepository = trackedWorkRepository;
        this.sourceChapterRepository = sourceChapterRepository;
        this.findPublicWork = findPublicWork;
        this.adminEmails = adminEmails;
        this.emailService = emailService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    /** Sob lock no lease: duas execuções de sync não mandam o resumo duas vezes. */
    void sendIfDue() {
        transactionTemplate.executeWithoutResult(tx -> leaseRepository.lockBySource(SourceName.MANGADEX).ifPresent(lease -> {
            ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZONE);
            OffsetDateTime dueAt = now.toLocalDate().atTime(SEND_AFTER).atZone(ZONE).toOffsetDateTime();
            if (now.toOffsetDateTime().isBefore(dueAt) || lease.digestSentSince(dueAt)) {
                return;
            }
            OffsetDateTime since = lease.getLastDigestAt() != null ? lease.getLastDigestAt() : dueAt.minusDays(1);
            List<String> lines = lines(since);
            lease.markDigest(now.toOffsetDateTime());
            if (lines.isEmpty()) {
                return;
            }
            List<String> recipients = adminEmails.handle();
            emailService.sendSourcingDigest(recipients, "[Burūna] Sync do MangaDex: " + lines.size()
                    + (lines.size() == 1 ? " aviso" : " avisos"), body(lines));
            log.info("Resumo do sync enviado a {} ADMs com {} avisos", recipients.size(), lines.size());
        }));
    }

    private List<String> lines(OffsetDateTime since) {
        List<String> lines = new ArrayList<>();
        for (TrackedWork tracked : trackedWorkRepository.findAllByOrderByCreatedAtDesc()) {
            String title = findPublicWork.getPublicWorkInfo(tracked.getWorkId()).title();
            if (tracked.getPausedReason().isPresent() && tracked.getPausedAt() != null && tracked.getPausedAt().isAfter(since)) {
                lines.add(title + ": " + tracked.getPausedReason().get());
            } else if (tracked.isActive() && tracked.getConsecutiveFailures() > 0) {
                lines.add(title + ": falha ao verificar a fonte (" + tracked.getConsecutiveFailures() + " seguidas)");
            }
            for (SourceAlert alert : SourceAlerts.of(tracked, sourceChapterRepository.findByTrackedWorkId(tracked.getId()))) {
                if (alert.detectedAt() != null && alert.detectedAt().isAfter(since)) {
                    lines.add(title + ", " + chapterName(alert) + ": " + LABELS.get(alert.type()));
                }
            }
        }
        return lines;
    }

    private String body(List<String> lines) {
        StringBuilder body = new StringBuilder("Desde o último resumo:\n\n");
        lines.stream().limit(MAX_LINES).forEach(line -> body.append("- ").append(line).append('\n'));
        if (lines.size() > MAX_LINES) {
            body.append("- e mais ").append(lines.size() - MAX_LINES).append('\n');
        }
        body.append("\nPara decidir o que fazer: ").append(frontendUrl).append("/admin/import\n");
        return body.toString();
    }

    private static String chapterName(SourceAlert alert) {
        String name = alert.number() != null ? "cap. " + alert.number() : alert.title() != null ? alert.title() : "oneshot";
        return name + " (" + alert.language() + ")";
    }
}
