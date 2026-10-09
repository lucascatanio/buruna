package com.buruna.work.persistence;

import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    boolean existsByWorkIdAndLanguageAndNumberAndStatusIn(UUID workId, String language, BigDecimal number,
                                                          Collection<ChapterStatus> statuses);
}
