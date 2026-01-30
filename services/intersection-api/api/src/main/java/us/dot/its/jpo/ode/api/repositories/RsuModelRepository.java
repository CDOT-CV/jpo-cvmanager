package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;

import java.util.Optional;

@Repository
public interface RsuModelRepository extends JpaRepository<RsuModel, Integer> {

    Optional<RsuModel> findByNameAndManufacturer(String name, String manufacturer);
}
