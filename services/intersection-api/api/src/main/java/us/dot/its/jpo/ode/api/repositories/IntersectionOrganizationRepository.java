package us.dot.its.jpo.ode.api.repositories;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.Intersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.IntersectionOrganization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntersectionOrganizationRepository extends JpaRepository<IntersectionOrganization, Integer> {

    @Modifying
    @Transactional
    void deleteIntersectionOrganizationByIntersection_IntersectionNumber(String intersectionNumber);

    @Modifying
    @Transactional
    @Query("DELETE FROM IntersectionOrganization io WHERE io.intersection.intersectionNumber = :intersectionNumber AND io.organization.name IN :orgNames")
    void deleteByIntersectionNumberAndOrganizationNameIn(@Param("intersectionNumber") String intersectionNumber,
            @Param("orgNames") List<String> orgNames);

    @Query("SELECT DISTINCT i FROM Intersection i " +
            "LEFT JOIN FETCH i.intersectionOrganizations io " +
            "LEFT JOIN FETCH io.organization " +
            "WHERE NOT EXISTS " +
            "(SELECT 1 FROM IntersectionOrganization io2 " +
            "WHERE io2.intersection.id = i.id AND io2.organization = :organization)")
    List<Intersection> findAllIntersectionsNotInOrganization(Organization organization);

    Optional<IntersectionOrganization> findByIntersection_IntersectionNumberAndOrganization(
            String intersectionNumber, Organization organization);

    @Modifying
    @Transactional
    @Query("DELETE FROM IntersectionOrganization io WHERE io.intersection.intersectionNumber IN :intersectionNumbers AND io.organization = :organization")
    void deleteByIntersectionNumbersAndOrganization(@Param("intersectionNumbers") List<String> intersectionNumbers,
            @Param("organization") Organization organization);

    @Query("SELECT CASE WHEN COUNT(io) > 0 THEN true ELSE false END "
            + "FROM IntersectionOrganization io "
            + "WHERE io.organization = :organization "
            + "AND (SELECT COUNT(io2) FROM IntersectionOrganization io2 WHERE io2.intersection.id = io.intersection.id) = 1")
    boolean existsOrphanIntersectionInOrganization(@Param("organization") Organization organization);

    @Modifying
    @Transactional
    @Query("DELETE FROM IntersectionOrganization io WHERE io.organization = :organization")
    void deleteAllByOrganization(@Param("organization") Organization organization);
}
