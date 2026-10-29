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

    @Query("SELECT COALESCE(SUM(c.fileSizeBytes), 0) FROM Chapter c "
            + "WHERE c.workId IN (SELECT w.id FROM Work w WHERE w.ownerId = :ownerId AND w.isPublic = false)")
    long sumPrivateFileBytesByOwnerId(@Param("ownerId") UUID ownerId);

    // ordem de leitura: número crescente; capítulos sem número (extras) por último, na ordem de envio
    @Query("SELECT c FROM Chapter c WHERE c.workId = :workId AND c.status IN :statuses "
            + "ORDER BY c.number ASC NULLS LAST, c.createdAt ASC")
    List<Chapter> findInReadingOrder(@Param("workId") UUID workId,
                                     @Param("statuses") Collection<ChapterStatus> statuses);

    @Query("SELECT c FROM Chapter c WHERE c.workId = :workId AND c.language = :language AND c.status IN :statuses "
            + "ORDER BY c.number ASC NULLS LAST, c.createdAt ASC")
    List<Chapter> findInReadingOrder(@Param("workId") UUID workId, @Param("language") String language,
                                     @Param("statuses") Collection<ChapterStatus> statuses);

    @Query("SELECT c.id FROM Chapter c WHERE c.workId = :workId AND c.language = :language AND c.status = :status "
            + "ORDER BY c.number ASC NULLS LAST, c.createdAt ASC")
    List<UUID> findIdsInReadingOrder(@Param("workId") UUID workId, @Param("language") String language,
                                     @Param("status") ChapterStatus status);

    interface LanguageCount {
        String getLanguage();

        long getChapterCount();
    }

    @Query("SELECT c.language AS language, COUNT(c) AS chapterCount FROM Chapter c "
            + "WHERE c.workId = :workId AND c.status = :status GROUP BY c.language ORDER BY c.language")
    List<LanguageCount> countByLanguage(@Param("workId") UUID workId, @Param("status") ChapterStatus status);

    @Query("SELECT c.id FROM Chapter c WHERE c.workId = :workId AND c.status = :status")
    List<UUID> findIdsByWorkIdAndStatus(@Param("workId") UUID workId, @Param("status") ChapterStatus status);
}
