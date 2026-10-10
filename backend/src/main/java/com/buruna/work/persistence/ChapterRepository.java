package com.buruna.work.persistence;

import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    boolean existsByWorkIdAndLanguageAndNumberAndStatusIn(UUID workId, String language, BigDecimal number,
                                                          Collection<ChapterStatus> statuses);

    boolean existsByWorkIdAndLanguageAndNumberAndStatusInAndIdNot(UUID workId, String language, BigDecimal number,
                                                                  Collection<ChapterStatus> statuses, UUID id);

    @EntityGraph(attributePaths = "pages")
    Optional<Chapter> findWithPagesById(UUID id);

    @EntityGraph(attributePaths = "pages")
    List<Chapter> findWithPagesByWorkIdIn(Collection<UUID> workIds);

    // uso da quota: páginas e arquivos enviados ainda guardados, só das obras privadas do dono
    @Query("SELECT COALESCE(SUM(p.sizeBytes), 0) FROM Chapter c JOIN c.pages p "
            + "WHERE c.workId IN (SELECT w.id FROM Work w WHERE w.ownerId = :ownerId AND w.isPublic = false)")
    long sumPrivatePageBytesByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT COALESCE(SUM(c.sourceSizeBytes), 0) FROM Chapter c "
            + "WHERE c.workId IN (SELECT w.id FROM Work w WHERE w.ownerId = :ownerId AND w.isPublic = false)")
    long sumPrivateSourceBytesByOwnerId(@Param("ownerId") UUID ownerId);
}
