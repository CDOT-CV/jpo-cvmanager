package us.dot.its.jpo.ode.api.models.postgres.projections;

import lombok.AllArgsConstructor;
import lombok.Getter;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.ScmsHealth;

@Getter
@AllArgsConstructor
public class ScmsHealthRsuProjection {
    private final Rsu rsu;
    private final ScmsHealth scmsHealth;
}