package us.dot.its.jpo.ode.api.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;

@Repository
public interface FirmwareUploadRepository extends JpaRepository<FirmwareUpload, UUID> {
}
