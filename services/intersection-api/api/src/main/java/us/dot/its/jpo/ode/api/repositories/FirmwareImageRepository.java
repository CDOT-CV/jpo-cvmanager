package us.dot.its.jpo.ode.api.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareImage;

public interface FirmwareImageRepository extends JpaRepository<FirmwareImage, Integer> {
    java.util.List<FirmwareImage> findByVerifiedUploadIdIn(java.util.Collection<java.util.UUID> uploadIds);
    Optional<FirmwareImage> findByModelIdAndVersion(Integer modelId, String version);
    boolean existsByModelIdAndVersion(Integer modelId, String version);
}
