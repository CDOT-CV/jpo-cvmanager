package us.dot.its.jpo.ode.api.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareImage;

@Repository
public interface ObuFirmwareImageRepository extends JpaRepository<ObuFirmwareImage, Integer> {

    @Query("select image from ObuFirmwareImage image join fetch image.manufacturer order by image.id")
    List<ObuFirmwareImage> findAllWithManufacturer();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select image from ObuFirmwareImage image where image.id = :id")
    Optional<ObuFirmwareImage> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select image from ObuFirmwareImage image
            where image.storageProvider = :provider and image.storageContainer = :container
              and image.objectName = :name order by image.id
            """)
    List<ObuFirmwareImage> findDestinationForUpdate(@Param("provider") String provider,
            @Param("container") String container, @Param("name") String name);

    Optional<ObuFirmwareImage> findByManufacturerIdAndFileName(Integer manufacturerId, String fileName);

    boolean existsByManufacturerIdAndFileName(Integer manufacturerId, String fileName);

    Optional<ObuFirmwareImage> findByVerifiedUploadId(UUID uploadId);
}
