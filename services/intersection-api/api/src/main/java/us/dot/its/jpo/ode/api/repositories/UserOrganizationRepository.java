package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserOrganization;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserOrganizationRepository extends JpaRepository<UserOrganization, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM UserOrganization uo WHERE uo.user.email = :email")
    void removeUserOrganizationByEmail(@Param("email") String email);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserOrganization uo WHERE uo.user.email IN :emails")
    void removeMultipleUserOrganizationsByEmail(@Param("emails") List<String> emails);

    @Query("SELECT uo FROM UserOrganization uo WHERE uo.user.email = :email")
    List<UserOrganization> findAllByEmail(@Param("email") String email);

    Optional<UserOrganization> findByOrganization_Name(String organizationName);

    Optional<UserOrganization> findByUserAndOrganization(User user, Organization organization);

    Optional<UserOrganization> findByUser_EmailAndOrganization(String email, Organization organization);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserOrganization uo WHERE uo.user.email IN :emails AND uo.organization = :organization")
    void deleteByUserEmailsAndOrganization(@Param("emails") List<String> emails,
            @Param("organization") Organization organization);

    @Query("SELECT uo.user.email FROM UserOrganization uo WHERE uo.organization.id = :orgId")
    List<String> findAllUserEmailsByOrganizationId(@Param("orgId") Integer orgId);

    @Query("SELECT DISTINCT u FROM User u WHERE NOT EXISTS " +
            "(SELECT 1 FROM UserOrganization uo WHERE uo.user.id = u.id AND uo.organization = :organization)")
    List<User> findAllUserEmailsNotInOrganization(
            @Param("organization") Organization organization);

    @Query("SELECT CASE WHEN COUNT(uo) > 0 THEN true ELSE false END "
            + "FROM UserOrganization uo "
            + "WHERE uo.organization = :organization "
            + "AND (SELECT COUNT(uo2) FROM UserOrganization uo2 WHERE uo2.user.id = uo.user.id) = 1")
    boolean existsOrphanUserInOrganization(@Param("organization") Organization organization);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserOrganization uo WHERE uo.organization = :organization")
    void deleteAllByOrganization(@Param("organization") Organization organization);
}
