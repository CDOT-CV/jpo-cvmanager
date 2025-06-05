package us.dot.its.jpo.ode.api.models.snmp;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RsuState {
    /**
     * Time in UTC Milliseconds that the RSU State was generated
     */
    public long timestamp;

    /**
     * IntersectionID of the intersection corresponding to the RSU
     */
    public int intersectionID;

    /**
     * IP Address of the RSU unit
     */
    public String rsuIP;

    /**
     * Internal Temperature of the RSU Unit
     */
    public double temperature;

    /**
     * Time in seconds since the RSU unit last rebooted
     */
    public int uptime;
}
