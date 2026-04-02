package us.dot.its.jpo.ode.api.models.postgres.projections;

import lombok.AllArgsConstructor;
import lombok.Getter;
import us.dot.its.jpo.ode.api.models.postgres.tables.ScmsHealth;

import java.net.InetAddress;

@Getter
@AllArgsConstructor
public class ScmsHealthRsuProjection {
    private final InetAddress rsuIp;
    private final ScmsHealth scmsHealth;
}