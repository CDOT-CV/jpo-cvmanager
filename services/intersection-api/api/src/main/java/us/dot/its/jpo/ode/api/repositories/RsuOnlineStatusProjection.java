package us.dot.its.jpo.ode.api.repositories;

import java.net.InetAddress;
import java.time.Instant;

/** Row returned while calculating the rolling online status of an RSU. */
public interface RsuOnlineStatusProjection {
    InetAddress getIpv4Address();

    Instant getTimestamp();

    Boolean getResult();
}
