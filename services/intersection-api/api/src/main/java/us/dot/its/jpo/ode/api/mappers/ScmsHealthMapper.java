package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import us.dot.its.jpo.ode.api.config.DateTimeConfig;
import us.dot.its.jpo.ode.api.models.postgres.projections.ScmsHealthRsuProjection;
import us.dot.its.jpo.ode.api.models.scms.ScmsHealthDto;
import us.dot.its.jpo.ode.api.models.scms.ScmsHealthResponse;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MapStruct mapper for SCMS health data.
 *
 * <p>MapStruct generates {@link #toDto(ScmsHealthRsuProjection)} with compile-time field checking.
 * If a field is added to {@link ScmsHealthDto} without a corresponding mapping, MapStruct emits
 * a compile error (see {@code unmappedTargetPolicy}).</p>
 *
 * <p>{@link #toResponse(List)} is manually implemented because MapStruct does not yet support
 * {@code List → Map} conversions keyed by a property. It delegates to the generated {@code toDto()}
 * to preserve compile-time checking.</p>
 *
 * @see <a href="https://github.com/mapstruct/mapstruct/discussions/3263">MapStruct Discussion #3263</a>
 * @see <a href="https://github.com/mapstruct/mapstruct/issues/3580">MapStruct Issue #3580</a>
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public abstract class ScmsHealthMapper {

    private static final String DATE_TIME_PATTERN = "MM/dd/yyyy hh:mm:ss a";

    @Autowired
    protected DateTimeConfig dateTimeConfig;

    /**
     * Maps a single projection to DTO. MapStruct generates this method.
     */
    @Mapping(target = "health", source = "health")
    @Mapping(target = "expiration", source = "expiration", qualifiedByName = "formatInstant")
    public abstract ScmsHealthDto toDto(ScmsHealthRsuProjection projection);

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
     * Converts projections to a response keyed by IP address.
     * Delegates to {@link #toDto} to preserve compile-time field checking.
     */
    public ScmsHealthResponse toResponse(List<ScmsHealthRsuProjection> projections) {
        if (projections == null) {
            return null;
        }
        Map<String, ScmsHealthDto> scmsHealthByIp = new HashMap<>();
        for (ScmsHealthRsuProjection projection : projections) {
            String ip = projection.getIpv4Address().getHostAddress();
            ScmsHealthDto dto = projection.getHealth() != null ? toDto(projection) : null;
            scmsHealthByIp.put(ip, dto);
        }
        return new ScmsHealthResponse(scmsHealthByIp);
    }
}