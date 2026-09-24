package com.buruna.manga.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolumeObjectNameTest {

    private static final UUID MANGA_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_MANGA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void pendingFor_returnsPathNamespacedByMangaId() {
        String pending = VolumeObjectName.pendingFor(MANGA_ID);

        assertThat(pending).matches("^pending/volumes/" + MANGA_ID + "/[0-9a-f-]{36}\\.pdf$");
    }

    @Test
    void pendingFor_eachCall_returnsDifferentFileId() {
        String first = VolumeObjectName.pendingFor(MANGA_ID);
        String second = VolumeObjectName.pendingFor(MANGA_ID);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void parsePending_validPendingForSameManga_returnsFinalObjectNameUnderVolumes() {
        String pending = VolumeObjectName.pendingFor(MANGA_ID);

        VolumeObjectName parsed = VolumeObjectName.parsePending(pending, MANGA_ID);

        assertThat(parsed.finalObjectName()).matches("^volumes/" + MANGA_ID + "/[0-9a-f-]{36}\\.pdf$");
    }

    @Test
    void parsePending_mangaIdDivergentFromRequest_throws() {
        // vetor do FIND-002: objectName pendente de UM mangá, finalize pedido para OUTRO.
        String pendingForOtherManga = VolumeObjectName.pendingFor(OTHER_MANGA_ID);

        assertThatThrownBy(() -> VolumeObjectName.parsePending(pendingForOtherManga, MANGA_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_alreadyFinalizedObjectName_throws() {
        // objectName de um volume público já finalizado (formato "volumes/...", sem "pending/")
        // é o outro vetor do FIND-002: reaproveitar o objectName exposto na URL de leitura.
        String finalized = "volumes/" + MANGA_ID + "/" + UUID.randomUUID() + ".pdf";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(finalized, MANGA_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_legacyUnscopedObjectName_throws() {
        // formato pré-ADR-40 (sem mangaId no path) não deve mais validar.
        String legacy = "pending/volumes/" + UUID.randomUUID() + ".pdf";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(legacy, MANGA_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_pathTraversal_throws() {
        String traversal = "pending/volumes/" + MANGA_ID + "/../../etc/passwd";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(traversal, MANGA_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_invalidUuidSegment_throws() {
        String invalidUuid = "pending/volumes/" + MANGA_ID + "/not-a-uuid.pdf";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(invalidUuid, MANGA_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_wrongExtension_throws() {
        String wrongExt = "pending/volumes/" + MANGA_ID + "/" + UUID.randomUUID() + ".exe";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(wrongExt, MANGA_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_extraPathSegments_throws() {
        String extraSegment = "pending/volumes/" + MANGA_ID + "/" + UUID.randomUUID() + "/extra.pdf";

        assertThatThrownBy(() -> VolumeObjectName.parsePending(extraSegment, MANGA_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }

    @Test
    void parsePending_nullObjectName_throws() {
        assertThatThrownBy(() -> VolumeObjectName.parsePending(null, MANGA_ID))
                .isInstanceOf(InvalidVolumeObjectNameException.class);
    }
}
