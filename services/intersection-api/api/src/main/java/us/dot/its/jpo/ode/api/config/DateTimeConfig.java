package us.dot.its.jpo.ode.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/**
 * Date and time configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class DateTimeConfig {

    /**
     * The timezone used for formatting dates in API responses.
     * Must be a valid IANA timezone identifier (e.g., "America/Denver", "UTC").
     */
    private String timezone = "America/Denver";

    /**
     * Returns the configured timezone as a ZoneId.
     * ZoneId.of() uses internal caching, so this is efficient.
     */
    public ZoneId getZoneId() {
        return ZoneId.of(timezone);
    }
}
