package com.buruna.work.application;

import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.domain.Language;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.ChapterRepository;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lista os capítulos de uma obra em ordem de leitura. Quem lê vê só os publicados; o dono da
 * obra (e o ADMIN, no catálogo) vê também os em processamento e os que falharam, com o motivo.
 * A lista vem filtrada por idioma: uma obra longa em vários idiomas não chega inteira no 4G.
 */
@Service
public class ListChaptersUseCase {

    private static final Set<ChapterStatus> PUBLISHED = EnumSet.of(ChapterStatus.PUBLISHED);
    private static final Set<ChapterStatus> ALL = EnumSet.allOf(ChapterStatus.class);

    private final WorkRepository workRepository;
    private final ChapterRepository chapterRepository;
    private final PrivateWorkAccess privateAccess;

    public ListChaptersUseCase(WorkRepository workRepository, ChapterRepository chapterRepository,
                               PrivateWorkAccess privateAccess) {
        this.workRepository = workRepository;
        this.chapterRepository = chapterRepository;
        this.privateAccess = privateAccess;
    }

    /** Catálogo público. {@code language} nulo traz todos os idiomas. */
    @Transactional(readOnly = true)
    public List<ChapterListItem> listPublic(UUID workId, String language, UUID actorId, boolean isAdmin) {
        Work work = workRepository.findById(workId)
                .filter(Work::isPublic)
                .orElseThrow(() -> new WorkNotFoundException(workId));
        boolean manages = isAdmin || work.getOwnerId().equals(actorId);
        return list(workId, language, manages ? ALL : PUBLISHED);
    }

    /** Coleção privada: só o dono, com todos os status. */
    @Transactional(readOnly = true)
    public List<ChapterListItem> listPrivate(UUID workId, String language, UUID actorId) {
        privateAccess.findOwned(workId, actorId);
        return list(workId, language, ALL);
    }

    /** Idiomas com capítulos publicados, para o seletor de idioma da obra. */
    @Transactional(readOnly = true)
    public List<ChapterLanguageResponse> publishedLanguages(UUID workId) {
        workRepository.findById(workId)
                .filter(Work::isPublic)
                .orElseThrow(() -> new WorkNotFoundException(workId));
        return chapterRepository.countByLanguage(workId, ChapterStatus.PUBLISHED).stream()
                .map(c -> new ChapterLanguageResponse(c.getLanguage(), c.getChapterCount()))
                .toList();
    }

    private List<ChapterListItem> list(UUID workId, String language, Set<ChapterStatus> statuses) {
        var chapters = language == null || language.isBlank()
                ? chapterRepository.findInReadingOrder(workId, statuses)
                : chapterRepository.findInReadingOrder(workId, Language.of(language).value(), statuses);
        return chapters.stream().map(ChapterListItem::from).toList();
    }
}
