package com.buruna.work.application;

import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterNumber;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.domain.DuplicateChapterException;
import com.buruna.work.domain.Language;
import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.ChapterRepository;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Registra um capítulo em processamento. A regra "número único por obra e idioma" é checada
 * por consulta, sem UNIQUE no banco (ADR-53): o lock na linha da obra serializa dois registros
 * simultâneos na mesma obra (sync e upload do ADM, ou uma reentrega), e o segundo vê o
 * primeiro. Obras diferentes não esperam umas pelas outras. Capítulo FAILED não ocupa o
 * número, para a nova tentativa poder registrar de novo.
 */
@Service
public class RegisterChapterUseCase {

    private static final Set<ChapterStatus> OCCUPYING_NUMBER =
            EnumSet.of(ChapterStatus.PROCESSING, ChapterStatus.PUBLISHED, ChapterStatus.UNPUBLISHED);

    private final WorkRepository workRepository;
    private final ChapterRepository chapterRepository;

    public RegisterChapterUseCase(WorkRepository workRepository, ChapterRepository chapterRepository) {
        this.workRepository = workRepository;
        this.chapterRepository = chapterRepository;
    }

    @Transactional
    public ChapterResponse handle(RegisterChapterCommand command) {
        workRepository.lockById(command.workId())
                .orElseThrow(() -> new WorkNotFoundException(command.workId()));

        Language language = Language.of(command.language());
        ChapterNumber number = command.number() == null ? null : ChapterNumber.of(command.number());

        if (number != null && chapterRepository.existsByWorkIdAndLanguageAndNumberAndStatusIn(
                command.workId(), language.value(), number.value(), OCCUPYING_NUMBER)) {
            throw new DuplicateChapterException(number, language);
        }

        Chapter chapter = Chapter.register(command.workId(), language, number, command.label(),
                command.title(), command.scanlationGroup(), command.kind(), command.uploadedById());
        return ChapterResponse.from(chapterRepository.save(chapter));
    }
}
