package us.dot.its.jpo.ode.api.accessors.bsm;

import org.springframework.data.domain.Page;

import us.dot.its.jpo.ode.api.models.DataLoader;
import us.dot.its.jpo.ode.model.OdeBsmData;

public interface OdeBsmJsonRepository extends DataLoader<OdeBsmData> {
    Page<OdeBsmData> find(String originIp, String vehicleId, Long startTime, Long endTime,
            Double longitude, Double latitude, Double distance, Integer pageNumber, int limit);

    long count(String originIp, String vehicleId, Long startTime, Long endTime, Double longitude,
            Double latitude, Double distance);
}