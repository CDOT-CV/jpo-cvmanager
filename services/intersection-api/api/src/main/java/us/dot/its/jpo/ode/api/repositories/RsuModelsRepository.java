package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;

import java.util.List;
import java.util.Optional;

@Repository
public interface RsuModelsRepository extends JpaRepository<RsuModel, Integer> {

    /**
     * Find RSU model by name
     */
    Optional<RsuModel> findByName(String name);

    /**
     * Get all RSU models with manufacturer names
     */
    @Query("SELECT m.name as manufacturerName, rm.name as rsuModelName " +
            "FROM RsuModels rm " +
            "JOIN Manufacturers m ON rm.manufacturer = m.manufacturerId " +
            "ORDER BY m.name ASC, rm.name ASC")
    List<RsuModelWithManufacturerProjection> findAllModelsWithManufacturers();

    interface RsuModelWithManufacturerProjection {
        String getManufacturerName();

        String getRsuModelName();
    }
}
