package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;

@Repository
@RepositoryRestResource(exported = false)
public interface ManufacturerRepository extends JpaRepository<Manufacturer, Integer> {
}