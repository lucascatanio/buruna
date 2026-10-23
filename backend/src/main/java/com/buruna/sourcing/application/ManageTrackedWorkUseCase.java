package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.ChapterSelection;
import com.buruna.sourcing.domain.InvalidSourceActionException;
import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.SourceChapterNotFoundException;
import com.buruna.sourcing.domain.SourceChapterState;
import com.buruna.sourcing.domain.TrackedWork;
import com.buruna.sourcing.domain.TrackedWorkNotFoundException;
import com.buruna.sourcing.persistence.SourceChapterRepository;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import com.buruna.work.application.ChapterActor;
import com.buruna.work.application.ChapterScope;
import com.buruna.work.application.DeleteChapterUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ações do ADM numa obra acompanhada (ADR-49). Toda ação que coloca algo na fila pede uma
 * rodada do executor depois do commit. As gravações travam a linha da obra acompanhada, como a
 * listagem, para não cruzar com uma rodada em andamento (ADR-53).
 */
@Service
public class ManageTrackedWorkUseCase {

    private final TrackedWorkRepository trackedWorkRepository;
    private final SourceChapterRepository sourceChapterRepository;
    private final DeleteChapterUseCase deleteChapter;
    private final SourcingTrigger trigger;
    private final Clock clock;

    public ManageTrackedWorkUseCase(TrackedWorkRepository trackedWorkRepository,
                                    SourceChapterRepository sourceChapterRepository,
                                    DeleteChapterUseCase deleteChapter,
                                    SourcingTrigger trigger,
                                    Clock clock) {
        this.trackedWorkRepository = trackedWorkRepository;
        this.sourceChapterRepository = sourceChapterRepository;
        this.deleteChapter = deleteChapter;
        this.trigger = trigger;
        this.clock = clock;
    }

    /** "Verificar agora": lista a obra na próxima rodada, que começa já. */
    @Transactional
    public void check(UUID trackedWorkId) {
        TrackedWork tracked = lock(trackedWorkId);
        if (!tracked.isActive()) {
            throw new InvalidSourceActionException("Retome a obra antes de verificar.");
        }
        tracked.requestListing();
        trigger.requestRun();
    }

    @Transactional
    public void pause(UUID trackedWorkId) {
        lock(trackedWorkId).pause(OffsetDateTime.now(clock));
    }

    @Transactional
    public void resume(UUID trackedWorkId) {
        lock(trackedWorkId).resume();
        trigger.requestRun();
    }

    /**
     * Ignora um grupo (pedido de remoção dele): apaga os capítulos que vieram dele e deixa a
     * escolha de versão trazer a de outro grupo, quando houver.
     */
    @Transactional
    public void ignoreGroup(UUID trackedWorkId, String groupId, String groupName, UUID adminId) {
        TrackedWork tracked = lock(trackedWorkId);
        tracked.ignoreGroup(groupId, groupName);
        List<SourceChapter> versions = sourceChapterRepository.findByTrackedWorkId(trackedWorkId);
        ChapterActor admin = new ChapterActor(adminId, true, null);
        for (SourceChapter version : versions) {
            if (!version.hasGroup(groupId)) {
                continue;
            }
            if (version.getChapterId().isPresent()) {
                deleteChapter.handle(tracked.getWorkId(), version.getChapterId().get(), ChapterScope.PUBLIC, admin);
            }
            version.detachFromChapter();
        }
        ChapterSelection.apply(versions, tracked::preferredGroupFor, tracked.ignoredGroupIds());
        trigger.requestRun();
    }

    /** Volta a aceitar o grupo; números sem capítulo podem passar a vir dele. */
    @Transactional
    public void stopIgnoringGroup(UUID trackedWorkId, String groupId) {
        TrackedWork tracked = lock(trackedWorkId);
        tracked.stopIgnoringGroup(groupId);
        ChapterSelection.apply(sourceChapterRepository.findByTrackedWorkId(trackedWorkId),
                tracked::preferredGroupFor, tracked.ignoredGroupIds());
        trigger.requestRun();
    }

    /** Tenta de novo uma versão que falhou de vez; a contagem recomeça. */
    @Transactional
    public void retry(UUID sourceChapterId) {
        SourceChapter version = version(sourceChapterId);
        if (version.getState() != SourceChapterState.FAILED) {
            throw new InvalidSourceActionException("Só uma versão com falha pode ser tentada de novo.");
        }
        version.retry(true);
        trigger.requestRun();
    }

    /** Baixa de novo as páginas de uma versão que a fonte trocou, no mesmo capítulo. */
    @Transactional
    public void redownload(UUID sourceChapterId) {
        SourceChapter version = version(sourceChapterId);
        if (version.getState() != SourceChapterState.READY || version.getChapterId().isEmpty()) {
            throw new InvalidSourceActionException("Só uma versão já baixada pode ser baixada de novo.");
        }
        version.requestReplacement(version.getChapterId().get());
        trigger.requestRun();
    }

    /**
     * Usa esta versão no lugar da que entrou (o grupo preferido publicou depois, ou o ADM prefere
     * outra tradução). O capítulo continua o mesmo, com as páginas trocadas; sem capítulo naquele
     * número, a versão entra como capítulo novo.
     */
    @Transactional
    public void useVersion(UUID sourceChapterId) {
        SourceChapter version = version(sourceChapterId);
        TrackedWork tracked = trackedWorkRepository.findById(version.getTrackedWorkId()).orElseThrow();
        if (version.isRemovedAtSource() || version.hasAnyGroup(tracked.ignoredGroupIds())) {
            throw new InvalidSourceActionException("Esta versão saiu do MangaDex ou é de um grupo ignorado.");
        }
        if (version.getState() == SourceChapterState.READY || version.getReplacesChapterId().isPresent()) {
            throw new InvalidSourceActionException("Esta versão já está no capítulo.");
        }
        String slot = ChapterSelection.slotOf(version);
        Optional<SourceChapter> holder = sourceChapterRepository.findByTrackedWorkId(version.getTrackedWorkId()).stream()
                .filter(v -> !v.getId().equals(version.getId()) && ChapterSelection.slotOf(v).equals(slot))
                .filter(v -> v.getState() == SourceChapterState.READY && v.getChapterId().isPresent())
                .findFirst();
        if (holder.isPresent()) {
            version.requestReplacement(holder.get().getChapterId().get());
        } else {
            version.retry(true);
            version.select();
        }
        trigger.requestRun();
    }

    @Transactional(readOnly = true)
    public List<SourceAlert> alerts(UUID trackedWorkId) {
        TrackedWork tracked = trackedWorkRepository.findById(trackedWorkId)
                .orElseThrow(() -> new TrackedWorkNotFoundException(trackedWorkId));
        return SourceAlerts.of(tracked, sourceChapterRepository.findByTrackedWorkId(trackedWorkId));
    }

    /** Grupos que publicaram versões desta obra, com quantas cada um, e se está ignorado. */
    @Transactional(readOnly = true)
    public List<TrackedGroupResponse> groups(UUID trackedWorkId) {
        TrackedWork tracked = trackedWorkRepository.findById(trackedWorkId)
                .orElseThrow(() -> new TrackedWorkNotFoundException(trackedWorkId));
        Map<String, TrackedGroupResponse> groups = new LinkedHashMap<>();
        for (SourceChapter version : sourceChapterRepository.findByTrackedWorkId(trackedWorkId)) {
            if (version.getGroupIds() == null) {
                continue;
            }
            String[] ids = version.getGroupIds().split(",");
            String[] names = version.getGroupNames() == null ? new String[0] : version.getGroupNames().split(" & ");
            for (int i = 0; i < ids.length; i++) {
                String name = i < names.length ? names[i] : ids[i];
                groups.merge(ids[i], new TrackedGroupResponse(ids[i], name, 1, tracked.isIgnored(ids[i])),
                        (a, b) -> new TrackedGroupResponse(a.id(), a.name(), a.versionCount() + 1, a.ignored()));
            }
        }
        tracked.getIgnoredGroups().forEach(group -> groups.putIfAbsent(group.getGroupId(),
                new TrackedGroupResponse(group.getGroupId(), group.getGroupName(), 0, true)));
        return groups.values().stream()
                .sorted(Comparator.comparingInt(TrackedGroupResponse::versionCount).reversed())
                .toList();
    }

    private TrackedWork lock(UUID trackedWorkId) {
        return trackedWorkRepository.lockById(trackedWorkId)
                .orElseThrow(() -> new TrackedWorkNotFoundException(trackedWorkId));
    }

    /** A versão, com a obra acompanhada dela travada (como na listagem). */
    private SourceChapter version(UUID sourceChapterId) {
        SourceChapter version = sourceChapterRepository.findById(sourceChapterId)
                .orElseThrow(() -> new SourceChapterNotFoundException(sourceChapterId));
        lock(version.getTrackedWorkId());
        return version;
    }
}
