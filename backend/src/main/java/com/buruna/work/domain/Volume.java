package com.buruna.work.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Volume de um mangá — entidade interna ao agregado {@link Work}. Criado via
 * {@link Work#addVolume}; não tem setters públicos. A referência ao usuário que
 * fez o upload é por id primitivo ({@code uploadedById}), sem entidade User de
 * outro contexto no domínio de work (ADR-35).
 */
@Entity
@Table(
        name = "volumes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"work_id", "volume_number"})
)
@Getter
@NoArgsConstructor
public class Volume {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private Work work;

    @Column(name = "volume_number", nullable = false)
    private Integer volumeNumber;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Volume(Work work, int volumeNumber, String fileUrl, String fileHash,
                  long fileSizeBytes, UUID uploadedById) {
        this.work = work;
        this.volumeNumber = volumeNumber;
        this.fileUrl = fileUrl;
        this.fileHash = fileHash;
        this.fileSizeBytes = fileSizeBytes;
        this.uploadedById = uploadedById;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
