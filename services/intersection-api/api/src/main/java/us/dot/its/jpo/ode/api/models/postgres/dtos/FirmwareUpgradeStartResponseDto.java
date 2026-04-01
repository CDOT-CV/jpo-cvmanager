package us.dot.its.jpo.ode.api.models.postgres.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * DTO for bulk RSU firmware upgrade start response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing the results of firmware upgrade operations for multiple RSUs")
public class FirmwareUpgradeStartResponseDto implements Serializable {
    @Schema(description = "Map of RSU IP addresses to their individual upgrade results")
    @JsonProperty("results")
    @NotNull
    Map<String, FirmwareUpgradeResultDto> results;
}
