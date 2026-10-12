package us.dot.its.jpo.ode.api.models.postgres.projections;

import java.net.InetAddress;
import java.time.Instant;

/**
 * Interface-based projection for RSU online-status queries.
 * Spring Data JPA maps selected columns to getter methods.
 */
public interface RsuOnlineStatusProjection {

    /**
     * @return The RSU IPv4 address (column: ipv4_address)
     */
    InetAddress getIpv4Address();

    /**
     * @return The ping timestamp (column: timestamp).
     *         Null when the RSU has no ping in the status window.
     */
    Instant getTimestamp();

    /**
     * @return The ping result (column: result).
     *         True if the ping succeeded, false otherwise.
     */
    Boolean getResult();
}
