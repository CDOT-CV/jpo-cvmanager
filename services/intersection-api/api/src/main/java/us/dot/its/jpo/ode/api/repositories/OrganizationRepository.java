package us.dot.its.jpo.ode.api.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Integer> {

    Optional<Organization> findByName(String name);

    List<Organization> findByIdIn(List<Integer> organizationIds);
}
