package us.dot.its.jpo.ode.api.accessors.counts;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import us.dot.its.jpo.ode.api.models.DataLoader;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.model.OdeBsmData;

public interface CountsRepository extends DataLoader<OdeBsmData> {
    Page<OdeBsmData> find(String originIp, String vehicleId, Long startTime, Long endTime,
            Double longitude, Double latitude, Double distance, Pageable pageable);

    long count(String originIp, String vehicleId, Long startTime, Long endTime, Double longitude,
            Double latitude, Double distance);

    /**
     * Get message counts for an RSU over a specified timespan
     * 
     * @param rsuIp     the RSU IP address
     * @param startTime start time in UTC milliseconds
     * @param endTime   end time in UTC milliseconds
     * @return list of message counts
     */
    List<MessageCount> getMessageCounts(String rsuIp, Long startTime, Long endTime);
}