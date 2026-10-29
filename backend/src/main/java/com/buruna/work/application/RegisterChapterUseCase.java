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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

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
        lockWork(command.workId());
        assertNumberFree(command.workId(), command.language(), command.number());
        Language language = Language.of(command.language());
        ChapterNumber number = command.number() == null ? null : ChapterNumber.of(command.number());

        Chapter chapter = Chapter.register(command.workId(), language, number, command.label(),
                command.title(), command.scanlationGroup(), command.kind(), command.uploadedById());
        if (command.sourceObjectName() != null) {
            chapter.attachSource(command.sourceObjectName(),
                    command.sourceSizeBytes() == null ? 0 : command.sourceSizeBytes());
        }
        return ChapterResponse.from(chapterRepository.save(chapter));
    }

    /** Trava a obra para serializar os registros de capítulo dela. Exige transação ativa. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockWork(UUID workId) {
        workRepository.lockById(workId).orElseThrow(() -> new WorkNotFoundException(workId));
    }

    /** Só tem efeito sob {@link #lockWork}: sem o lock, dois registros simultâneos passariam. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertNumberFree(UUID workId, String languageTag, BigDecimal rawNumber) {
        assertNumberFree(workId, languageTag, rawNumber, null);
    }

    /** Igual ao anterior, sem contar o próprio capítulo ({@code ignoredChapterId}), para o retry. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertNumberFree(UUID workId, String languageTag, BigDecimal rawNumber, UUID ignoredChapterId) {
        if (rawNumber == null) {
            return;
        }
        Language language = Language.of(languageTag);
        ChapterNumber number = ChapterNumber.of(rawNumber);
        boolean taken = ignoredChapterId == null
                ? chapterRepository.existsByWorkIdAndLanguageAndNumberAndStatusIn(
                        workId, language.value(), number.value(), OCCUPYING_NUMBER)
                : chapterRepository.existsByWorkIdAndLanguageAndNumberAndStatusInAndIdNot(
                        workId, language.value(), number.value(), OCCUPYING_NUMBER, ignoredChapterId);
        if (taken) {
            throw new DuplicateChapterException(number, language);
        }
    }
}
