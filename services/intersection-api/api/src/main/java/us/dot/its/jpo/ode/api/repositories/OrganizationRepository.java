package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Integer> {

    Optional<Organization> findById(Integer id);

    List<Organization> findByIdIn(List<Integer> ids);

    @Query("SELECT o.id FROM Organization o ORDER BY o.id ASC")
    List<Integer> findAllOrganizationIds();
}
