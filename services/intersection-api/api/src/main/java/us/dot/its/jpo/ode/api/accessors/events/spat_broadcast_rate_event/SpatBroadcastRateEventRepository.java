package us.dot.its.jpo.ode.api.accessors.events.spat_broadcast_rate_event;

import java.util.List;

import org.springframework.data.domain.Page;

import us.dot.its.jpo.conflictmonitor.monitor.models.events.broadcast_rate.SpatBroadcastRateEvent;
import us.dot.its.jpo.ode.api.models.IDCount;
import us.dot.its.jpo.ode.api.models.DataLoader;

public interface SpatBroadcastRateEventRepository extends DataLoader<SpatBroadcastRateEvent> {
    long count(Integer intersectionID, Long startTime, Long endTime);

    Page<SpatBroadcastRateEvent> findLatest(Integer intersectionID, Long startTime, Long endTime);

    Page<SpatBroadcastRateEvent> find(Integer intersectionID, Long startTime, Long endTime, Integer pageNumber,
            int limit);

    List<IDCount> getAggregatedDailySpatBroadcastRateEventCounts(int intersectionID, Long startTime, Long endTime);
}