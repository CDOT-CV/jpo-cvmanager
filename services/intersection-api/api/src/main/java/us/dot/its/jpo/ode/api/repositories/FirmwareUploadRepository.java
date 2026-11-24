package us.dot.its.jpo.ode.api.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;

@Repository
public interface FirmwareUploadRepository extends UploadLifecycleRepository<FirmwareUpload> {
    @Query(value = """
            select distinct on (object_name) * from firmware_uploads
            where storage_provider = :provider and storage_container = :container
            order by object_name, (status = 'VERIFIED') desc, created_at desc, upload_id
            """, nativeQuery = true)
    List<FirmwareUpload> findListingCandidates(@Param("provider") String provider,
            @Param("container") String container);

    // Prefer verified evidence for each object, falling back to its newest upload
    // attempt when no verified record exists
    @Query(value = """
            select distinct on (object_name) * from firmware_uploads
            where storage_provider = :provider and storage_container = :container and object_name in (:names)
            order by object_name, (status = 'VERIFIED') desc, created_at desc, upload_id
            """, nativeQuery = true)
    List<FirmwareUpload> findListingUploads(@Param("provider") String provider,
            @Param("container") String container, @Param("names") Collection<String> names);
}
