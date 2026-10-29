package com.buruna.work.persistence;

import com.buruna.work.domain.Volume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VolumeRepository extends JpaRepository<Volume, UUID> {

    boolean existsByWorkIdAndVolumeNumber(UUID workId, Integer volumeNumber);

    Optional<Volume> findByIdAndWorkId(UUID id, UUID workId);

    List<Volume> findByWorkId(UUID workId);

    /** Retorna IDs dos volumes de um mangá ordenados por volume_number DESC (para o contexto reading). */
    @Query("SELECT v.id FROM Volume v WHERE v.work.id = :workId ORDER BY v.volumeNumber DESC")
    List<UUID> findIdsByWorkIdOrderByVolumeNumberDesc(@Param("workId") UUID workId);

    // soma os bytes de todos os volumes privados do usuário, usado para validação de cota
    @Query("SELECT COALESCE(SUM(v.fileSizeBytes), 0) FROM Volume v " +
            "WHERE v.work.ownerId = :ownerId AND v.work.isPublic = false")
    long sumPrivateFileSizeByOwnerId(@Param("ownerId") UUID ownerId);

    boolean existsByFileHashAndWorkIsPublicTrue(String fileHash);

    /** Dos {@code fileUrls} informados, devolve só os que têm linha em {@code volumes}. */
    @Query("SELECT DISTINCT v.fileUrl FROM Volume v WHERE v.fileUrl IN :fileUrls")
    List<String> findExistingFileUrls(@Param("fileUrls") Collection<String> fileUrls);

    boolean existsByFileUrlAndIdNot(String fileUrl, UUID id);

    @Query("""
    SELECT v.work.ownerId AS ownerId, SUM(v.fileSizeBytes) AS totalBytes
    FROM Volume v
    WHERE v.work.isPublic = false
    GROUP BY v.work.ownerId
    """)
    List<VolumeStorageProjection> findStorageByOwner();
}