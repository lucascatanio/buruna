package com.buruna.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade rica de usuário (ADR-32: domínio rico anotado com JPA). A criação passa
 * pela fábrica {@link #register} e toda mutação de estado ocorre por métodos de
 * negócio com invariantes — não há setters expostos. VOs ({@link Email},
 * {@link Username}, {@link Quota}) validam na borda da application.
 */
@Entity
@Table(name = "users")
@Getter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "presentation_message", nullable = false, columnDefinition = "TEXT")
    private String presentationMessage;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "user_role")
    private Role role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "user_status")
    private UserStatus status;

    @Column(name = "quota_gb", nullable = false, precision = 10, scale = 2)
    private BigDecimal quotaGb;

    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    @Column(name = "last_access_at")
    private OffsetDateTime lastAccessAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected User() {
    }

    /**
     * Cria um novo usuário no estado inicial de cadastro: {@code PENDING}, papel
     * {@code READER} e 2FA desligado. Os VOs garantem e-mail/username/cota válidos.
     */
    public static User register(Email email, Username username, String passwordHash,
                                String presentationMessage, Quota quota) {
        User user = new User();
        user.email = email.value();
        user.username = username.value();
        user.passwordHash = passwordHash;
        user.presentationMessage = presentationMessage;
        user.role = Role.READER;
        user.status = UserStatus.PENDING;
        user.quotaGb = quota.gigabytes();
        user.totpEnabled = false;
        return user;
    }

    // ── Transições de status ────────────────────────────────────────────────

    /** Aprova um cadastro pendente: {@code PENDING → ACTIVE}. */
    public void approve() {
        if (status != UserStatus.PENDING) {
            throw new UserNotPendingException();
        }
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Valida que o cadastro pode ser rejeitado (precisa estar {@code PENDING}).
     * A remoção em si é responsabilidade do repositório na camada application.
     */
    public void reject() {
        if (status != UserStatus.PENDING) {
            throw new UserNotPendingException();
        }
    }

    /** Desativa um usuário ativo (job de inatividade): {@code ACTIVE → INACTIVE}. */
    public void deactivate() {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException("Só é possível desativar um usuário ACTIVE (atual: " + status + ")");
        }
        this.status = UserStatus.INACTIVE;
    }

    /** Override administrativo de status (PATCH /admin/users/{id}/status). */
    public void changeStatus(UserStatus newStatus) {
        this.status = newStatus;
    }

    // ── Outras mutações de negócio ──────────────────────────────────────────

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public void changeQuota(Quota quota) {
        this.quotaGb = quota.gigabytes();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void assignAvatar(String avatarObjectName) {
        this.avatarUrl = avatarObjectName;
    }

    public void recordLogin(OffsetDateTime when) {
        this.lastAccessAt = when;
    }

    // ── 2FA (TOTP) ──────────────────────────────────────────────────────────

    /** Inicia o setup de 2FA gravando o secret. Falha se o 2FA já estiver ativo. */
    public void startTotpSetup(String secret) {
        if (totpEnabled) {
            throw new IllegalStateException("2FA is already enabled");
        }
        this.totpSecret = secret;
    }

    public void enableTotp() {
        this.totpEnabled = true;
    }

    public void disableTotp() {
        this.totpEnabled = false;
        this.totpSecret = null;
    }

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
