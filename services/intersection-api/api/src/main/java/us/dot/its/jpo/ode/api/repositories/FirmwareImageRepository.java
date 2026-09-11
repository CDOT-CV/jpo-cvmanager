package us.dot.its.jpo.ode.api.repositories;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareImage;

public interface FirmwareImageRepository extends JpaRepository<FirmwareImage, Integer> {
    // Legacy images have no upload link, so identify their file using the existing
    // manufacturer/model/version/package convention as well.
    @Query(value = """
            select image.* from firmware_images image
            join rsu_models model on model.rsu_model_id = image.model
            join manufacturers manufacturer on manufacturer.manufacturer_id = model.manufacturer
            left join firmware_uploads upload on upload.upload_id = image.verified_upload_id
            where (upload.storage_provider = :provider and upload.storage_container = :container
                   and upload.object_name = :name)
               or (image.verified_upload_id is null and
                   concat(manufacturer.name, '/', model.name, '/', image.version, '/', image.install_package) = :name)
            order by image.firmware_id for update of image
            """, nativeQuery = true)
    List<FirmwareImage> findDestinationForUpdate(@Param("provider") String provider,
            @Param("container") String container, @Param("name") String name);

    List<FirmwareImage> findByVerifiedUploadIdIn(Collection<UUID> uploadIds);
    Optional<FirmwareImage> findByModelIdAndVersion(Integer modelId, String version);
    boolean existsByModelIdAndVersion(Integer modelId, String version);
}
