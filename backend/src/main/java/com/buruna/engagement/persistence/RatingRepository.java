package com.buruna.engagement.persistence;

import com.buruna.engagement.domain.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    Optional<Rating> findByUserIdAndWorkId(UUID userId, UUID workId);

    void deleteByUserIdAndWorkId(UUID userId, UUID workId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.workId = :workId")
    int countByWorkId(@Param("workId") UUID workId);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM Rating r WHERE r.workId = :workId")
    double avgScoreByWorkId(@Param("workId") UUID workId);
}
