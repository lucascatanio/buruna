package com.buruna.sourcing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Garante um executor por fonte: com dois baixando ao mesmo tempo, o limite de taxa da fonte
 * seria estourado. Quem segura o lease o renova enquanto trabalha; se morrer, o lease vence
 * sozinho em {@code lockedUntil}. Ler e alterar sob lock na linha (ADR-53).
 */
@Entity
@Table(name = "source_leases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceLease {

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "external_source")
    private SourceName source;

    @Column(length = 100)
    private String holder;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_digest_at")
    private OffsetDateTime lastDigestAt;

    /** Pega ou renova o lease. Falso se outro executor o segura e ele ainda não venceu. */
    public boolean tryAcquire(String candidate, OffsetDateTime now, Duration ttl) {
        boolean free = holder == null || lockedUntil == null || !lockedUntil.isAfter(now);
        if (!free && !holder.equals(candidate)) {
            return false;
        }
        holder = candidate;
        lockedUntil = now.plus(ttl);
        return true;
    }

    /** O e-mail diário de hoje já saiu (ou não havia o que mandar) a partir de {@code dueAt}. */
    public boolean digestSentSince(OffsetDateTime dueAt) {
        return lastDigestAt != null && !lastDigestAt.isBefore(dueAt);
    }

    public void markDigest(OffsetDateTime now) {
        lastDigestAt = now;
    }

    public void release(String candidate) {
        if (candidate.equals(holder)) {
            holder = null;
            lockedUntil = null;
        }
    }
}
