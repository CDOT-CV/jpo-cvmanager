package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;

import java.util.Optional;

@Repository
@RepositoryRestResource(exported = false)
public interface RsuModelRepository extends JpaRepository<RsuModel, Integer> {

    @Query("SELECT rm FROM RsuModel rm " +
            "JOIN rm.manufacturer m " +
            "WHERE rm.name = :name AND m.name = :manufacturerName")
    Optional<RsuModel> findByNameAndManufacturerName(@Param("name") String name,
            @Param("manufacturerName") String manufacturerName);
}
