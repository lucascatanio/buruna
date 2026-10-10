package com.buruna.work.application;

import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterAccessDeniedException;
import com.buruna.work.domain.ChapterKind;
import com.buruna.work.domain.ChapterNotFoundException;
import com.buruna.work.domain.ChapterNumber;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.ChapterRepository;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leitura de capítulos para o contexto {@code reading} (ADR-39: o acesso cross-contexto passa
 * por use case público). A regra de acesso é do contexto {@code work}: só capítulo publicado, de
 * obra pública ou do próprio leitor. Capítulo não publicado responde como inexistente.
 */
@Service
public class GetChapterForReadingUseCase {

    private final ChapterRepository chapterRepository;
    private final WorkRepository workRepository;

    public GetChapterForReadingUseCase(ChapterRepository chapterRepository, WorkRepository workRepository) {
        this.chapterRepository = chapterRepository;
        this.workRepository = workRepository;
    }

    /**
     * Abre o capítulo para leitura. {@code countView} falso é o pré-carregamento do próximo
     * capítulo pelo leitor: devolve as páginas sem contar visualização.
     */
    @Transactional
    public ChapterReadingView open(UUID chapterId, UUID actorId, boolean countView) {
        Chapter chapter = chapterRepository.findWithPagesById(chapterId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));
        Work work = readableWork(chapter, actorId);
        if (countView) {
            work.registerView();
        }

        List<UUID> sameLanguage = chapterRepository.findIdsInReadingOrder(chapter.getWorkId(),
                chapter.getLanguage().value(), ChapterStatus.PUBLISHED);
        int position = sameLanguage.indexOf(chapterId);
        UUID previous = position > 0 ? sameLanguage.get(position - 1) : null;
        UUID next = position >= 0 && position < sameLanguage.size() - 1 ? sameLanguage.get(position + 1) : null;

        List<ChapterReadingView.Page> pages = chapter.getPages().stream()
                .map(p -> new ChapterReadingView.Page(p.getObjectName(),
                        p.getDataSaverObjectName().orElse(null), p.getWidth(), p.getHeight()))
                .toList();
        return new ChapterReadingView(chapter.getId(), chapter.getWorkId(), chapter.getLanguage().value(),
                chapter.getNumber().map(ChapterNumber::value).orElse(null), chapter.getLabel().orElse(null),
                chapter.getTitle().orElse(null), chapter.getScanlationGroup().orElse(null),
                pages, previous, next);
    }

    /** Confere o acesso sem efeito colateral, para salvar progresso. */
    @Transactional(readOnly = true)
    public ChapterAccessInfo validateAccess(UUID chapterId, UUID actorId) {
        Chapter chapter = chapterRepository.findWithPagesById(chapterId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));
        readableWork(chapter, actorId);
        return new ChapterAccessInfo(chapter.getId(), chapter.getWorkId(), chapter.getPages().size());
    }

    /** Capítulos publicados da obra, para o progresso por obra ("continuar lendo"). */
    @Transactional(readOnly = true)
    public List<UUID> publishedChapterIds(UUID workId, UUID actorId) {
        Work work = workRepository.findById(workId).orElseThrow(() -> new WorkNotFoundException(workId));
        if (!work.isPublic() && !work.getOwnerId().equals(actorId)) {
            throw new WorkNotFoundException(workId);
        }
        return chapterRepository.findIdsByWorkIdAndStatus(workId, ChapterStatus.PUBLISHED);
    }

    /** Identificação dos capítulos, para o histórico. Ids inexistentes ficam de fora do mapa. */
    @Transactional(readOnly = true)
    public Map<UUID, ChapterInfo> getInfoByIds(Collection<UUID> chapterIds) {
        if (chapterIds.isEmpty()) {
            return Map.of();
        }
        return chapterRepository.findAllById(chapterIds).stream()
                .map(c -> new ChapterInfo(c.getId(), c.getWorkId(), c.getLanguage().value(),
                        c.getNumber().map(ChapterNumber::value).orElse(null), c.getLabel().orElse(null)))
                .collect(Collectors.toMap(ChapterInfo::chapterId, Function.identity()));
    }

    private Work readableWork(Chapter chapter, UUID actorId) {
        if (chapter.getStatus() != ChapterStatus.PUBLISHED || chapter.getKind() != ChapterKind.PAGES) {
            throw new ChapterNotFoundException(chapter.getId());
        }
        Work work = workRepository.findById(chapter.getWorkId())
                .orElseThrow(() -> new ChapterNotFoundException(chapter.getId()));
        if (!work.isPublic() && !work.getOwnerId().equals(actorId)) {
            throw new ChapterAccessDeniedException(chapter.getId());
        }
        return work;
    }
}
