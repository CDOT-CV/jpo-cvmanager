package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Integer> {

    Organization findByName(String name);
}
