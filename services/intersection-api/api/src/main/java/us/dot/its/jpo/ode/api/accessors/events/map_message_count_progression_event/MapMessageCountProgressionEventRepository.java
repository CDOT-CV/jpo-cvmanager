package us.dot.its.jpo.ode.api.accessors.events.map_message_count_progression_event;

import org.springframework.data.domain.Page;
import us.dot.its.jpo.conflictmonitor.monitor.models.events.MapMessageCountProgressionEvent;
import us.dot.its.jpo.ode.api.models.DataLoader;

public interface MapMessageCountProgressionEventRepository extends DataLoader<MapMessageCountProgressionEvent> {
    long count(Integer intersectionID, Long startTime, Long endTime);

    Page<MapMessageCountProgressionEvent> findLatest(Integer intersectionID, Long startTime, Long endTime);

    Page<MapMessageCountProgressionEvent> find(Integer intersectionID, Long startTime, Long endTime, Integer pageNumber,
            int limit);
}