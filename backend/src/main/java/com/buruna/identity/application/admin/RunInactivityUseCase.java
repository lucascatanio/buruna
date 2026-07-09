package com.buruna.identity.application.admin;

import com.buruna.identity.domain.InactivityPolicy;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.manga.application.maintenance.DeletePrivateCollectionForUserUseCase;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.storage.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Caso de uso de inatividade: avisa usuários inativos e desativa os que passaram do limiar,
 * apagando sua coleção privada. Roda diariamente pelo {@link Scheduled} e pode ser disparado
 * manualmente pelo {@code JobController}.
 *
 * <p>Depende de {@code manga} SÓ pela camada application ({@link DeletePrivateCollectionForUserUseCase}) —
 * sem acesso a {@code manga.persistence}/{@code manga.domain} (ADR-35).
 *
 * <p><b>B1 (corrigido):</b> a leitura dos volumes da coleção privada ocorre DENTRO da transação
 * do use case de {@code manga}, invocado por proxy AOP válido — não há mais
 * {@code LazyInitializationException} por self-invocation.
 *
 * <p><b>B2 (corrigido):</b> os candidatos são selecionados de uma vez via
 * {@link UserRepository#findEligibleForInactivity} (só quem já cruzou o limiar de aviso),
 * em vez de paginar por offset um conjunto {@code ACTIVE} que muda de status no loop.
 *
 * <p>Tempo vem do {@link Clock} injetado (ADR-36), tornando os limiares testáveis com
 * {@code Clock.fixed(...)}.
 */
@Service
public class RunInactivityUseCase {

    private static final Logger log = LoggerFactory.getLogger(RunInactivityUseCase.class);

    private static final int WARNING_THRESHOLD_DAYS = 75;

    private final UserRepository userRepository;
    private final DeletePrivateCollectionForUserUseCase deletePrivateCollectionForUser;
    private final StorageClient storageClient;
    private final EmailService emailService;
    private final Clock clock;
    private final InactivityPolicy policy = new InactivityPolicy();

    public RunInactivityUseCase(UserRepository userRepository,
                                DeletePrivateCollectionForUserUseCase deletePrivateCollectionForUser,
                                StorageClient storageClient,
                                EmailService emailService,
                                Clock clock) {
        this.userRepository = userRepository;
        this.deletePrivateCollectionForUser = deletePrivateCollectionForUser;
        this.storageClient = storageClient;
        this.emailService = emailService;
        this.clock = clock;
    }

    // roda todo dia às 02:00
    @Scheduled(cron = "0 0 2 * * *")
    public void run() {
        log.info("RunInactivityUseCase started");

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime warningCutoff = now.minusDays(WARNING_THRESHOLD_DAYS);

        List<User> candidates =
                userRepository.findEligibleForInactivity(UserStatus.ACTIVE, warningCutoff);
        log.info("RunInactivityUseCase evaluating {} candidate(s)", candidates.size());

        int warned = 0;
        int deactivated = 0;

        for (User user : candidates) {
            OffsetDateTime lastAccess = user.getLastAccessAt();
            if (lastAccess == null) lastAccess = user.getCreatedAt();

            switch (policy.decide(lastAccess, now)) {
                case DEACTIVATE -> {
                    deactivate(user);
                    deactivated++;
                }
                case WARN -> {
                    emailService.sendInactivityWarning(user.getEmail(), user.getUsername());
                    warned++;
                }
                // Não deve ocorrer: a query só traz quem passou do limiar de aviso. Defensivo.
                case NONE -> { }
            }
        }

        log.info("RunInactivityUseCase finished: {} warned, {} deactivated", warned, deactivated);
    }

    private void deactivate(User user) {
        // Coleção privada apagada na transação do use case de manga (proxy AOP válido): os
        // volumes são lidos dentro dessa tx, o que elimina o LazyInitializationException (B1).
        List<String> objectNames = deletePrivateCollectionForUser.handle(user.getId());

        user.deactivate();
        userRepository.save(user);
        log.info("User {} deactivated due to inactivity", user.getUsername());

        // GCS fora da transação de deleção, best-effort: falha aqui não reverte o banco (órfãos tolerados).
        deleteFromGcs(objectNames);
    }

    private void deleteFromGcs(List<String> objectNames) {
        for (String objectName : objectNames) {
            try {
                storageClient.delete(objectName);
            } catch (RuntimeException e) {
                log.warn("Failed to delete GCS object {} (best-effort): {}", objectName, e.getMessage());
            }
        }
    }
}
