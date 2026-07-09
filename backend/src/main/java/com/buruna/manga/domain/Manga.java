package com.buruna.manga.domain;

import com.buruna.shared.converter.StringListConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Raiz do agregado de catálogo/coleção. Mangás públicos e privados compartilham a
 * tabela via {@code is_public} (decisão da Fase 1). Domínio rico (ADR-32/34): sem
 * setters públicos — mutações passam por métodos de negócio com invariantes.
 *
 * <p>Referências a usuários de outro contexto são por id primitivo
 * ({@code ownerId}, {@code reviewedById}) — sem entidade {@code User} no domínio de
 * manga (ADR-35). RBAC fica na borda (@PreAuthorize); ownership é verificado na
 * application comparando {@code ownerId} com o {@code actorId} autenticado.
 */
@Entity
@Table(name = "mangas")
@Getter
@NoArgsConstructor
public class Manga {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Convert(converter = StringListConverter.class)
    @Column(name = "alternative_titles", columnDefinition = "TEXT")
    private List<String> alternativeTitles = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "manga_format")
    private MangaFormat format;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status_origin", nullable = false, columnDefinition = "manga_status_origin")
    private MangaStatusOrigin statusOrigin;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status_site", nullable = false, columnDefinition = "manga_status_site")
    private MangaStatusSite statusSite;

    private Integer year;

    @Convert(converter = StringListConverter.class)
    @Column(name = "content_warnings", columnDefinition = "TEXT")
    private List<String> contentWarnings = new ArrayList<>();

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "submission_status", columnDefinition = "manga_submission_status")
    private MangaSubmissionStatus submissionStatus;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedById;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "manga_tags",
            joinColumns = @JoinColumn(name = "manga_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "manga", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("volumeNumber ASC")
    private List<Volume> volumes = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ── Fábricas ─────────────────────────────────────────────────────────────

    /** Cria um mangá já público (fluxo de catálogo). Detalhes via {@link #updateCatalogDetails}. */
    public static Manga createPublic(Slug slug, UUID ownerId) {
        Manga manga = new Manga();
        manga.slug = slug.value();
        manga.ownerId = ownerId;
        manga.isPublic = true;
        return manga;
    }

    /** Cria um mangá privado com os defaults da coleção pessoal. */
    public static Manga createPrivate(Slug slug, String title, String synopsis, UUID ownerId) {
        Manga manga = new Manga();
        manga.slug = slug.value();
        manga.title = title;
        manga.synopsis = synopsis;
        manga.ownerId = ownerId;
        manga.isPublic = false;
        manga.format = MangaFormat.MANGA;
        manga.statusOrigin = MangaStatusOrigin.ONGOING;
        manga.statusSite = MangaStatusSite.INCOMPLETE;
        return manga;
    }

    // ── Edição de metadados ──────────────────────────────────────────────────

    /** Aplica os campos editáveis do catálogo (não toca capa nem slug). */
    public void updateCatalogDetails(String title, List<String> alternativeTitles, String synopsis,
                                     MangaFormat format, String originCountry,
                                     MangaStatusOrigin statusOrigin, MangaStatusSite statusSite,
                                     Integer year, List<String> contentWarnings, Set<Tag> tags) {
        this.title = title;
        this.alternativeTitles = alternativeTitles != null ? alternativeTitles : new ArrayList<>();
        this.synopsis = synopsis;
        this.format = format;
        this.originCountry = originCountry;
        this.statusOrigin = statusOrigin;
        this.statusSite = statusSite;
        this.year = year;
        this.contentWarnings = contentWarnings != null ? contentWarnings : new ArrayList<>();
        this.tags = tags != null ? tags : new HashSet<>();
    }

    /** Atualiza os campos editáveis de um mangá privado. */
    public void updatePrivateDetails(String title, String synopsis) {
        this.title = title;
        this.synopsis = synopsis;
    }

    public void changeCover(String coverObjectName) {
        this.coverUrl = coverObjectName;
    }

    public void changeSlug(Slug slug) {
        this.slug = slug.value();
    }

    public void applyRatingStats(BigDecimal avgRating, int ratingCount) {
        this.avgRating = avgRating;
        this.ratingCount = ratingCount;
    }

    public void registerView() {
        this.viewCount = this.viewCount + 1;
    }

    // ── Volumes (invariante: número único dentro do agregado) ────────────────

    public Volume addVolume(VolumeNumber number, String fileUrl, FileHash hash,
                            long fileSizeBytes, UUID uploadedById) {
        boolean numberTaken = volumes.stream()
                .anyMatch(v -> v.getVolumeNumber() == number.value());
        if (numberTaken) {
            throw new DuplicateVolumeException(number.value());
        }
        Volume volume = new Volume(this, number.value(), fileUrl, hash.value(), fileSizeBytes, uploadedById);
        volumes.add(volume);
        return volume;
    }

    public Volume removeVolume(UUID volumeId) {
        Volume volume = volumes.stream()
                .filter(v -> v.getId().equals(volumeId))
                .findFirst()
                .orElseThrow(() -> new VolumeNotFoundException(volumeId));
        volumes.remove(volume);
        return volume;
    }

    // ── Submissão / moderação / promoção (sem receber User — ADR-35) ─────────

    public void submitForApproval() {
        if (isPublic) {
            throw new MangaAlreadyPublicException();
        }
        if (submissionStatus == MangaSubmissionStatus.PENDING) {
            throw new MangaAlreadySubmittedException();
        }
        submissionStatus = MangaSubmissionStatus.PENDING;
        submittedAt = OffsetDateTime.now();
        rejectionReason = null;
    }

    public void approve(UUID reviewerId) {
        if (submissionStatus != MangaSubmissionStatus.PENDING) {
            throw new SubmissionNotPendingException();
        }
        isPublic = true;
        submissionStatus = null;
        reviewedById = reviewerId;
        reviewedAt = OffsetDateTime.now();
    }

    public void reject(UUID reviewerId, String reason) {
        if (submissionStatus != MangaSubmissionStatus.PENDING) {
            throw new SubmissionNotPendingException();
        }
        submissionStatus = MangaSubmissionStatus.REJECTED;
        rejectionReason = reason;
        reviewedById = reviewerId;
        reviewedAt = OffsetDateTime.now();
    }

    /**
     * Marca o mangá como público. As verificações de conflito (título/hash/slug
     * duplicados na biblioteca pública) dependem do repositório e permanecem na
     * application (ver PromoteMangaUseCase no [4.5]).
     */
    public void promoteToPublic() {
        isPublic = true;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Manga manga)) return false;
        return id != null && id.equals(manga.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
