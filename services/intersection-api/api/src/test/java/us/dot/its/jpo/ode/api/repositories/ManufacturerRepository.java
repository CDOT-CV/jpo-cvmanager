package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;

/**
 * Repository for Manufacturer entities.
 * This repository is used in integration tests to populate necessary data for RsuModel and Rsu entities.
 * Spring Data JPA automatically generates the required implementation for standard CRUD operations.
 * If this repository gets used in the future by the actual production code, it should be moved to the main
 * directory (src/main/java) instead of the test directory (src/test/java).
 */
@Repository
public interface ManufacturerRepository extends JpaRepository<Manufacturer, Integer> {

}
