package com.buruna.work.application;

import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.persistence.ChapterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Marca um capítulo em processamento como FAILED, numa transação própria (quem chama está
 * fora de transação ou numa que vai ser desfeita). Falha transitória mantém o arquivo para
 * nova tentativa; arquivo inválido o descarta, porque tentar de novo não resolveria.
 */
@Service
public class FailChapterIngestUseCase {

    private final ChapterRepository chapterRepository;

    public FailChapterIngestUseCase(ChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void keepingSource(UUID chapterId, String reason) {
        processing(chapterId).ifPresent(chapter -> chapter.fail(reason));
    }

    /** Devolve o arquivo descartado, para quem chama apagar do storage. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> discardingSource(UUID chapterId, String reason) {
        return processing(chapterId).flatMap(chapter -> {
            chapter.fail(reason);
            return chapter.discardSource();
        });
    }

    private Optional<Chapter> processing(UUID chapterId) {
        return chapterRepository.findById(chapterId)
                .filter(chapter -> chapter.getStatus() == ChapterStatus.PROCESSING);
    }
}
