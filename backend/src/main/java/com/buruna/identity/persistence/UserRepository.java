package com.buruna.identity.persistence;

import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    long countByStatus(UserStatus status);

    List<User> findByStatus(UserStatus status);

    List<User> findByRoleAndStatus(Role role, UserStatus status);

    /**
     * Traz apenas os usuários {@code status} que já cruzaram o limiar de aviso
     * ({@code warnCutoff = now - 75d}) — inclusive quem nunca logou (via {@code createdAt}).
     * Substitui a paginação por offset do job de inatividade, que pulava usuários ao
     * reconsultar um conjunto que muda de status no loop (B2). Query intracontexto
     * (identity apenas) — não viola ADR-39.
     */
    @Query("SELECT u FROM User u WHERE u.status = :status "
            + "AND (u.lastAccessAt < :warnCutoff "
            + "OR (u.lastAccessAt IS NULL AND u.createdAt < :warnCutoff))")
    List<User> findEligibleForInactivity(@Param("status") UserStatus status,
                                         @Param("warnCutoff") OffsetDateTime warnCutoff);
}
