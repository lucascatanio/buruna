package com.buruna.work.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes puros (sem Spring) dos invariantes do agregado Work (ADR-38).
 * O caminho feliz de removeVolume depende de ids gerados pelo JPA e é coberto
 * pelo WorkIntegrationTest; aqui cobrimos o caso de volume inexistente.
 */
class WorkTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private Work privateWork() {
        return Work.createPrivate(Slug.of("teste"), "Teste", "sinopse", OWNER);
    }

    @Test
    void addVolume_assignsFieldsAndAppends() {
        Work work = privateWork();
        Volume v = work.addVolume(VolumeNumber.of(1), "volumes/a.pdf", FileHash.of("h1"), 1024L, OWNER);

        assertThat(work.getVolumes()).containsExactly(v);
        assertThat(v.getVolumeNumber()).isEqualTo(1);
        assertThat(v.getFileUrl()).isEqualTo("volumes/a.pdf");
        assertThat(v.getFileHash()).isEqualTo("h1");
        assertThat(v.getUploadedById()).isEqualTo(OWNER);
        assertThat(v.getWork()).isSameAs(work);
    }

    @Test
    void addVolume_duplicateNumber_throws() {
        Work work = privateWork();
        work.addVolume(VolumeNumber.of(1), "volumes/a.pdf", FileHash.of("h1"), 1024L, OWNER);

        assertThatThrownBy(() ->
                work.addVolume(VolumeNumber.of(1), "volumes/b.pdf", FileHash.of("h2"), 1024L, OWNER))
                .isInstanceOf(DuplicateVolumeException.class);
    }

    @Test
    void removeVolume_nonexistent_throws() {
        Work work = privateWork();
        assertThatThrownBy(() -> work.removeVolume(UUID.randomUUID()))
                .isInstanceOf(VolumeNotFoundException.class);
    }

    @Test
    void submitForApproval_setsPending() {
        Work work = privateWork();
        work.submitForApproval();

        assertThat(work.getSubmissionStatus()).isEqualTo(WorkSubmissionStatus.PENDING);
        assertThat(work.getSubmittedAt()).isNotNull();
        assertThat(work.getRejectionReason()).isNull();
    }

    @Test
    void submitForApproval_whenAlreadyPending_throws() {
        Work work = privateWork();
        work.submitForApproval();
        assertThatThrownBy(work::submitForApproval)
                .isInstanceOf(WorkAlreadySubmittedException.class);
    }

    @Test
    void submitForApproval_whenPublic_throws() {
        Work work = Work.createPublic(Slug.of("pub"), OWNER);
        assertThatThrownBy(work::submitForApproval)
                .isInstanceOf(WorkAlreadyPublicException.class);
    }

    @Test
    void approve_makesPublicMarksApprovedAndRecordsReviewer() {
        Work work = privateWork();
        work.submitForApproval();
        work.approve(ADMIN);

        assertThat(work.isPublic()).isTrue();
        assertThat(work.getSubmissionStatus()).isEqualTo(WorkSubmissionStatus.APPROVED);
        assertThat(work.getReviewedById()).isEqualTo(ADMIN);
        assertThat(work.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_whenNotPending_throws() {
        Work work = privateWork();
        assertThatThrownBy(() -> work.approve(ADMIN))
                .isInstanceOf(SubmissionNotPendingException.class);
    }

    @Test
    void reject_setsRejectedWithReason() {
        Work work = privateWork();
        work.submitForApproval();
        work.reject(ADMIN, "qualidade");

        assertThat(work.getSubmissionStatus()).isEqualTo(WorkSubmissionStatus.REJECTED);
        assertThat(work.getRejectionReason()).isEqualTo("qualidade");
        assertThat(work.getReviewedById()).isEqualTo(ADMIN);
        assertThat(work.isPublic()).isFalse();
    }

    @Test
    void reject_whenNotPending_throws() {
        Work work = privateWork();
        assertThatThrownBy(() -> work.reject(ADMIN, "x"))
                .isInstanceOf(SubmissionNotPendingException.class);
    }

    @Test
    void promoteToPublic_flipsFlag() {
        Work work = privateWork();
        work.promoteToPublic();
        assertThat(work.isPublic()).isTrue();
    }

    @Test
    void promoteToPublic_withPendingSubmission_closesSubmissionWithoutStatus() {
        Work work = privateWork();
        work.submitForApproval();

        work.promoteToPublic();

        assertThat(work.isPublic()).isTrue();
        assertThat(work.getSubmissionStatus()).isNull();
    }

    @Test
    void promoteToPublic_afterRejection_clearsStatusAndReason() {
        Work work = privateWork();
        work.submitForApproval();
        work.reject(ADMIN, "qualidade");

        work.promoteToPublic();

        assertThat(work.getSubmissionStatus()).isNull();
        assertThat(work.getRejectionReason()).isNull();
    }

    @Test
    void approve_afterApproval_throws() {
        Work work = privateWork();
        work.submitForApproval();
        work.approve(ADMIN);

        assertThatThrownBy(() -> work.approve(ADMIN))
                .isInstanceOf(SubmissionNotPendingException.class);
    }

    @Test
    void registerView_incrementsCount() {
        Work work = privateWork();
        assertThat(work.getViewCount()).isZero();
        work.registerView();
        assertThat(work.getViewCount()).isEqualTo(1);
    }
}
