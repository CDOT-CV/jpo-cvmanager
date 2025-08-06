package us.dot.its.jpo.ode.api.accessors.events.map_broadcast_rate_event;

import java.util.List;

import org.springframework.data.domain.Page;

import us.dot.its.jpo.conflictmonitor.monitor.models.events.broadcast_rate.MapBroadcastRateEvent;
import us.dot.its.jpo.ode.api.models.IDCount;
import us.dot.its.jpo.ode.api.models.DataLoader;

public interface MapBroadcastRateEventRepository extends DataLoader<MapBroadcastRateEvent> {
    long count(Integer intersectionID, Long startTime, Long endTime);

    Page<MapBroadcastRateEvent> findLatest(Integer intersectionID, Long startTime, Long endTime);

    Page<MapBroadcastRateEvent> find(Integer intersectionID, Long startTime, Long endTime, Integer pageNumber,
            int limit);

    List<IDCount> getAggregatedDailyMapBroadcastRateEventCounts(int intersectionID, Long startTime, Long endTime);
}