package com.buruna.sourcing.persistence;

import com.buruna.sourcing.domain.SourceLease;
import com.buruna.sourcing.domain.SourceName;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SourceLeaseRepository extends JpaRepository<SourceLease, SourceName> {

    /** O lease da fonte, travado até o fim da transação. Também serializa criar acompanhamentos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from SourceLease l where l.source = :source")
    Optional<SourceLease> lockBySource(@Param("source") SourceName source);
}
