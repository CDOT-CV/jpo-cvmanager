package us.dot.its.jpo.ode.api.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.Intersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;

@Repository
public interface IntersectionRepository extends JpaRepository<Intersection, Integer> {

    /**
     * Finds a single intersection by its intersection_number.
     */
    Optional<Intersection> findByIntersectionNumber(String intersectionNumber);

    /**
     * Fetches a single intersection with its organization associations eagerly
     * loaded.
     * Avoids N+1 queries when reading org names.
     */
    @Query("SELECT DISTINCT i FROM Intersection i " +
            "LEFT JOIN FETCH i.intersectionOrganizations io " +
            "LEFT JOIN FETCH io.organization " +
            "WHERE i.intersectionNumber = :intersectionNumber")
    Optional<Intersection> findByIntersectionNumberWithOrgs(
            @Param("intersectionNumber") Integer intersectionNumber);

    /**
     * Fetches intersections belonging to a single organization, with org
     * associations loaded.
     */
    @Query("SELECT DISTINCT i FROM Intersection i " +
            "LEFT JOIN FETCH i.intersectionOrganizations io " +
            "LEFT JOIN FETCH io.organization " +
            "WHERE io.organization = :organization")
    List<Intersection> findAllByOrganization(Organization organization);

    @Query("SELECT i.intersectionNumber " +
            "FROM Intersection i " +
            "JOIN i.intersectionOrganizations io " +
            "JOIN io.organization.userOrganizations uo " +
            "JOIN uo.user u " +
            "WHERE u.email = :email")
    List<String> findAllowedIntersectionIdsByEmail(@Param("email") String email);

    @Query("SELECT i.intersectionNumber " +
            "FROM Intersection i " +
            "JOIN i.intersectionOrganizations io " +
            "JOIN io.organization o " +
            "WHERE o.name = :orgName")
    List<String> findIntersectionsByOrganization(@Param("orgName") String orgName);

    boolean existsByIntersectionNumberAndIntersectionOrganizationsOrganizationNameIn(String intersectionId,
            List<String> orgNames);
}