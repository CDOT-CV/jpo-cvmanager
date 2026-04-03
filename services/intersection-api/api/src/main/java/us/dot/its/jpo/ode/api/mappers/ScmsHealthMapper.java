package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import us.dot.its.jpo.ode.api.config.DateTimeConfig;
import us.dot.its.jpo.ode.api.models.postgres.projections.ScmsHealthRsuProjection;
import us.dot.its.jpo.ode.api.models.scms.ScmsHealthDto;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class ScmsHealthMapper {

    private static final String DATE_TIME_PATTERN = "MM/dd/yyyy hh:mm:ss a";

    @Autowired
    protected DateTimeConfig dateTimeConfig;

    @Mapping(target = "health", source = "health", qualifiedByName = "healthToString")
    @Mapping(target = "expiration", source = "expiration", qualifiedByName = "formatInstant")
    public abstract ScmsHealthDto toDto(ScmsHealthRsuProjection projection);

    @Named("healthToString")
    protected String healthToString(Boolean health) {
        if (health == null) {
            return null;
        }
        return health ? "1" : "0";
    }

    @Named("formatInstant")
    protected String formatInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)
                .withZone(dateTimeConfig.getZoneId());
        return formatter.format(instant);
    }

    /**
     * Converts a list of projections to a map keyed by IP address.
     * Entries with null health are mapped to null values.
     */
    public Map<String, ScmsHealthDto> toMap(List<ScmsHealthRsuProjection> queryResults) {
        if (queryResults == null) {
            return null;
        }

        Map<String, ScmsHealthDto> statusMap = new HashMap<>();
        for (ScmsHealthRsuProjection result : queryResults) {
            String ip = result.getIpv4Address().getHostAddress();

            if (result.getHealth() != null) {
                statusMap.put(ip, toDto(result));
            } else {
                statusMap.put(ip, null);
            }
        }
        return statusMap;
    }
}