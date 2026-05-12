package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
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

    Optional<UserOrganization> findByOrganization_Id(Integer organizationId);

    Optional<UserOrganization> findByUserAndOrganization_Id(User user, Integer organizationId);

    Optional<UserOrganization> findByUser_EmailAndOrganization_Id(String email, Integer organizationId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserOrganization uo WHERE uo.user.email IN :emails AND uo.organization.id = :orgId")
    void deleteByUserEmailsAndOrganizationId(@Param("emails") List<String> emails, @Param("orgId") Integer orgId);

    @Query("SELECT uo.user.email FROM UserOrganization uo WHERE uo.organization.id = :organizationId")
    List<String> findAllUserEmailsByOrganizationId(@Param("organizationId") Integer organizationId);

    @Query("SELECT DISTINCT u FROM User u WHERE NOT EXISTS " +
            "(SELECT 1 FROM UserOrganization uo WHERE uo.user.id = u.id AND uo.organization.id = :organizationId)")
    List<User> findAllUserEmailsNotInOrganizationId(
            @Param("organizationId") Integer organizationId);

    @Query("SELECT CASE WHEN COUNT(uo) > 0 THEN true ELSE false END "
            + "FROM UserOrganization uo "
            + "WHERE uo.organization.id = :orgId "
            + "AND (SELECT COUNT(uo2) FROM UserOrganization uo2 WHERE uo2.user.id = uo.user.id) = 1")
    boolean existsOrphanUserInOrganization(@Param("orgId") Integer orgId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserOrganization uo WHERE uo.organization.id = :orgId")
    void deleteAllByOrganizationId(@Param("orgId") Integer orgId);
}
