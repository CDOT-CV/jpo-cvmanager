package us.dot.its.jpo.ode.api.repositories;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpgradeRule;

@Repository
public interface FirmwareUpgradeRuleRepository extends JpaRepository<FirmwareUpgradeRule, Integer> {
    @Query("select rule.from.id from FirmwareUpgradeRule rule where rule.id = :id")
    Optional<Integer> findSourceId(@Param("id") Integer id);

    @Query("select rule from FirmwareUpgradeRule rule "
            + "join fetch rule.from source join fetch source.model sm join fetch sm.manufacturer "
            + "join fetch rule.to target join fetch target.model tm join fetch tm.manufacturer "
            + "left join fetch target.verifiedUpload order by rule.id")
    List<FirmwareUpgradeRule> findRulesWithImages();

    @Modifying
    @Query("delete from FirmwareUpgradeRule rule where rule.from.id in :ids or rule.to.id in :ids")
    void deleteForImages(@Param("ids") Collection<Integer> ids);

    Optional<FirmwareUpgradeRule> findFirstByFrom_Id(Integer fromId);
}
