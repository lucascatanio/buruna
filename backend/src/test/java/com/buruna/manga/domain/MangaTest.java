package com.buruna.manga.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes puros (sem Spring) dos invariantes do agregado Manga (ADR-38).
 * O caminho feliz de removeVolume depende de ids gerados pelo JPA e é coberto
 * pelo MangaIntegrationTest; aqui cobrimos o caso de volume inexistente.
 */
class MangaTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private Manga privateManga() {
        return Manga.createPrivate(Slug.of("teste"), "Teste", "sinopse", OWNER);
    }

    @Test
    void addVolume_assignsFieldsAndAppends() {
        Manga manga = privateManga();
        Volume v = manga.addVolume(VolumeNumber.of(1), "volumes/a.pdf", FileHash.of("h1"), 1024L, OWNER);

        assertThat(manga.getVolumes()).containsExactly(v);
        assertThat(v.getVolumeNumber()).isEqualTo(1);
        assertThat(v.getFileUrl()).isEqualTo("volumes/a.pdf");
        assertThat(v.getFileHash()).isEqualTo("h1");
        assertThat(v.getUploadedById()).isEqualTo(OWNER);
        assertThat(v.getManga()).isSameAs(manga);
    }

    @Test
    void addVolume_duplicateNumber_throws() {
        Manga manga = privateManga();
        manga.addVolume(VolumeNumber.of(1), "volumes/a.pdf", FileHash.of("h1"), 1024L, OWNER);

        assertThatThrownBy(() ->
                manga.addVolume(VolumeNumber.of(1), "volumes/b.pdf", FileHash.of("h2"), 1024L, OWNER))
                .isInstanceOf(DuplicateVolumeException.class);
    }

    @Test
    void removeVolume_nonexistent_throws() {
        Manga manga = privateManga();
        assertThatThrownBy(() -> manga.removeVolume(UUID.randomUUID()))
                .isInstanceOf(VolumeNotFoundException.class);
    }

    @Test
    void submitForApproval_setsPending() {
        Manga manga = privateManga();
        manga.submitForApproval();

        assertThat(manga.getSubmissionStatus()).isEqualTo(MangaSubmissionStatus.PENDING);
        assertThat(manga.getSubmittedAt()).isNotNull();
        assertThat(manga.getRejectionReason()).isNull();
    }

    @Test
    void submitForApproval_whenAlreadyPending_throws() {
        Manga manga = privateManga();
        manga.submitForApproval();
        assertThatThrownBy(manga::submitForApproval)
                .isInstanceOf(MangaAlreadySubmittedException.class);
    }

    @Test
    void submitForApproval_whenPublic_throws() {
        Manga manga = Manga.createPublic(Slug.of("pub"), OWNER);
        assertThatThrownBy(manga::submitForApproval)
                .isInstanceOf(MangaAlreadyPublicException.class);
    }

    @Test
    void approve_makesPublicAndRecordsReviewer() {
        Manga manga = privateManga();
        manga.submitForApproval();
        manga.approve(ADMIN);

        assertThat(manga.isPublic()).isTrue();
        assertThat(manga.getSubmissionStatus()).isNull();
        assertThat(manga.getReviewedById()).isEqualTo(ADMIN);
        assertThat(manga.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_whenNotPending_throws() {
        Manga manga = privateManga();
        assertThatThrownBy(() -> manga.approve(ADMIN))
                .isInstanceOf(SubmissionNotPendingException.class);
    }

    @Test
    void reject_setsRejectedWithReason() {
        Manga manga = privateManga();
        manga.submitForApproval();
        manga.reject(ADMIN, "qualidade");

        assertThat(manga.getSubmissionStatus()).isEqualTo(MangaSubmissionStatus.REJECTED);
        assertThat(manga.getRejectionReason()).isEqualTo("qualidade");
        assertThat(manga.getReviewedById()).isEqualTo(ADMIN);
        assertThat(manga.isPublic()).isFalse();
    }

    @Test
    void reject_whenNotPending_throws() {
        Manga manga = privateManga();
        assertThatThrownBy(() -> manga.reject(ADMIN, "x"))
                .isInstanceOf(SubmissionNotPendingException.class);
    }

    @Test
    void promoteToPublic_flipsFlag() {
        Manga manga = privateManga();
        manga.promoteToPublic();
        assertThat(manga.isPublic()).isTrue();
    }

    @Test
    void registerView_incrementsCount() {
        Manga manga = privateManga();
        assertThat(manga.getViewCount()).isZero();
        manga.registerView();
        assertThat(manga.getViewCount()).isEqualTo(1);
    }
}
