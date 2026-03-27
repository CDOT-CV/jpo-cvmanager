package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, Integer> {
}