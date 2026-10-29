package us.dot.its.jpo.ode.api.repositories;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.MaxRetryLimitReachedInstance;
import us.dot.its.jpo.ode.api.models.postgres.tables.MaxRetryLimitReachedInstanceId;

@Repository
public interface MaxRetryLimitReachedInstanceRepository
        extends JpaRepository<MaxRetryLimitReachedInstance, MaxRetryLimitReachedInstanceId> {

    @Modifying
    @Query("DELETE FROM MaxRetryLimitReachedInstance instance "
            + "WHERE instance.targetFirmwareVersion.id IN :ids")
    void deleteByTargetFirmwareVersionIdIn(@Param("ids") Collection<Integer> ids);
}
