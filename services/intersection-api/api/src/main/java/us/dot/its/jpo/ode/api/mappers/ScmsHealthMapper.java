package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import us.dot.its.jpo.ode.api.models.postgres.projections.ScmsHealthRsuProjection;
import us.dot.its.jpo.ode.api.models.postgres.tables.ScmsHealth;
import us.dot.its.jpo.ode.api.models.scms.ScmsHealthDto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface ScmsHealthMapper {

    String ZONE_ID = "America/Denver"; // TODO: make configurable
    DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a")
            .withZone(ZoneId.of(ZONE_ID));

    @Mapping(target = "health", source = "scmsHealth.health", qualifiedByName = "healthToString")
    @Mapping(target = "expiration", source = "scmsHealth.expiration", qualifiedByName = "formatInstant")
    ScmsHealthDto toDto(ScmsHealthRsuProjection projection);

    @Named("healthToString")
    default String healthToString(Boolean health) {
        if (health == null) {
            return null;
        }
        return health ? "1" : "0";
    }

    @Named("formatInstant")
    default String formatInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(instant);
    }

    default Map<String, ScmsHealthDto> toMap(List<ScmsHealthRsuProjection> queryResults) {
        if (queryResults == null) {
            return null;
        }

        Map<String, ScmsHealthDto> statusMap = new HashMap<>();
        for (ScmsHealthRsuProjection result : queryResults) {
            String ip = result.getRsuIp().getHostAddress();
            ScmsHealth sh = result.getScmsHealth();

            if (sh != null) {
                statusMap.put(ip, toDto(result));
            } else {
                statusMap.put(ip, null);
            }
        }
        return statusMap;
    }
}