package us.dot.its.jpo.ode.api.models.devices;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for {@code POST /devices/rsus/geo-query}.
 */
@Getter
@Setter
public class RsuGeoQueryRequest {

    /**
     * Polygon ring as {@code [longitude, latitude]} pairs. The ring should be closed.
     * Each point must contain at least two numbers.
     */
    @NotEmpty
    private List<List<Double>> geometry;

    /**
     * Manufacturer name used to restrict the result. {@code null}, blank, and
     * {@code "Select Vendor"} apply no manufacturer filter.
     */
    private String vendor;
}
