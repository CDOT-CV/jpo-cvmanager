package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    /**
     * Check if User exists in any of the given organizations using entity
     * relationships
     */
    boolean existsByEmailAndUserOrganizationsOrganizationIn(String email,
            List<Organization> organizations);

    /**
     * Check if all users in the list exist in at least one of the given
     * organizations.
     * Returns true only if ALL emails are found in at least one of the
     * organizations.
     */
    @Query("SELECT CASE WHEN COUNT(DISTINCT u.email) = :emailCount THEN true ELSE false END " +
            "FROM User u " +
            "JOIN u.userOrganizations uo " +
            "WHERE u.email IN :emails AND uo.organization IN :organizations")
    boolean allUsersExistInOrganizations(@Param("emails") List<String> emails,
            @Param("organizations") List<Organization> organizations,
            @Param("emailCount") long emailCount);

    Optional<User> findByEmail(String email);

    List<User> findByEmailIn(List<String> emails);

    @Query("SELECT user " +
            "FROM User user " +
            "JOIN user.userOrganizations ro " +
            "WHERE ro.organization = :organization " +
            "AND (:search IS NULL OR :search = '' OR " +
            "LOWER(CAST(user.email AS string)) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(CAST(user.firstName AS string)) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(CAST(user.lastName AS string)) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(CAST(user.superUser AS string)) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findAllByOrganization(@Param("organization") Organization organization, @Param("search") String search,
            Pageable pageable);

    @Query("SELECT o " +
            "FROM User u " +
            "JOIN u.userOrganizations ro " +
            "JOIN ro.organization o " +
            "WHERE u.email = :email " +
            "ORDER BY o.id ASC")
    List<Organization> findAllOrganizationsByEmail(@Param("email") String email);
}
