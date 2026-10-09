package com.buruna.work.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolumeObjectNameTest {

    private static final UUID WORK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_WORK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void pendingFor_returnsPathNamespacedByWorkId() {
        String pending = VolumeObjectName.pendingFor(WORK_ID);

        assertThat(pending).matches("^pending/volumes/" + WORK_ID + "/[0-9a-f-]{36}\\.pdf$");
    }

    @Test
    void pendingFor_eachCall_returnsDifferentFileId() {
        String first = VolumeObjectName.pendingFor(WORK_ID);
        String second = VolumeObjectName.pendingFor(WORK_ID);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void parsePending_validPendingForSameWork_returnsFinalObjectNameUnderVolumes() {
        String pending = VolumeObjectName.pendingFor(WORK_ID);

        VolumeObjectName parsed = VolumeObjectName.parsePending(pending, WORK_ID);

        assertThat(parsed.finalObjectName()).matches("^volumes/" + WORK_ID + "/[0-9a-f-]{36}\\.pdf$");
    }

    @Test
    void parsePending_workIdDivergentFromRequest_throws() {
        String pendingForOtherWork = VolumeObjectName.pendingFor(OTHER_WORK_ID);

        assertThatThrownBy(() -> VolumeObjectName.parsePending(pendingForOtherWork, WORK_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_alreadyFinalizedObjectName_throws() {
        // objectName de um volume público já finalizado (formato "volumes/...", sem "pending/")
        // é o vetor de ataque original: reaproveitar o objectName exposto na URL de leitura.
        String finalized = "volumes/" + WORK_ID + "/" + UUID.randomUUID() + ".pdf";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(finalized, WORK_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_legacyUnscopedObjectName_throws() {
        // formato pré-ADR-40 (sem workId no path) não deve mais validar.
        String legacy = "pending/volumes/" + UUID.randomUUID() + ".pdf";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(legacy, WORK_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_pathTraversal_throws() {
        String traversal = "pending/volumes/" + WORK_ID + "/../../etc/passwd";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(traversal, WORK_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_invalidUuidSegment_throws() {
        String invalidUuid = "pending/volumes/" + WORK_ID + "/not-a-uuid.pdf";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(invalidUuid, WORK_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_wrongExtension_throws() {
        String wrongExt = "pending/volumes/" + WORK_ID + "/" + UUID.randomUUID() + ".exe";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(wrongExt, WORK_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_extraPathSegments_throws() {
        String extraSegment = "pending/volumes/" + WORK_ID + "/" + UUID.randomUUID() + "/extra.pdf";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(extraSegment, WORK_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_nullObjectName_throws() {
        assertThatThrownBy(() -> VolumeObjectName.parsePending(null, WORK_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }
}
