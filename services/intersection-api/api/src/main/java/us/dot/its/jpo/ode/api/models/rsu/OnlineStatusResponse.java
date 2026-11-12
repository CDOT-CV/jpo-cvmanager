package us.dot.its.jpo.ode.api.models.rsu;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Response wrapper for the RSU online status endpoint.
 * <p>Using a dedicated response class (rather than returning {@code Map} directly) provides:</p>
 * <ul>
 *   <li>Clear API contract with explicit field names</li>
 *   <li>Easier extensibility if additional response fields are needed</li>
 *   <li>Better OpenAPI/Swagger documentation</li>
 * </ul>
 */
@Schema(description = "Response containing rolling online status for RSUs in an organization")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OnlineStatusResponse {

    @Schema(description = "Map of RSU IPv4 addresses to their current rolling online status.")
    private Map<String, OnlineStatusDto> onlineStatusByIp;
}
