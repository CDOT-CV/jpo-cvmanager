package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModels;

import java.util.Optional;

@Repository
public interface RsuModelsRepository extends JpaRepository<RsuModels, Integer> {

    /**
     * Find RSU model by name
     */
    Optional<RsuModels> findByName(String name);
}
