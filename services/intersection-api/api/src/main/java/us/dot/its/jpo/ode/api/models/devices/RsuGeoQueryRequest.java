package us.dot.its.jpo.ode.api.models.devices;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for {@code POST /devices/rsus/geo-query}.
 */
@Getter
@Setter
@Schema(description = "Polygon ring and optional manufacturer filter for an RSU geo query")
public class RsuGeoQueryRequest {

    /**
     * Polygon ring as {@code [longitude, latitude]} pairs. The ring must be closed
     * and contain at least four positions. Each coordinate must be a finite number.
     */
    @NotEmpty
    @Schema(description = "Closed polygon ring of [longitude, latitude] pairs. At least four positions, including the repeated closing point. Each coordinate must be a finite number.",
            example = "[[-105.34460908203145, 39.724583197251334], [-105.34666901855489, 39.670180083300174], [-105.25122529296911, 39.679162192647944], [-105.2539718750002, 39.72088725644132], [-105.34460908203145, 39.724583197251334]]")
    private List<List<Double>> geometry;

    /**
     * Manufacturer name used to restrict the result. {@code null}, blank, and
     * {@code "Select Vendor"} apply no manufacturer filter.
     */
    @Schema(description = "Manufacturer name. Null, blank, and \"Select Vendor\" apply no manufacturer filter.",
            example = "Commsignia")
    private String vendor;
}
