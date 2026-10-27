package us.dot.its.jpo.ode.api.models.devices.management;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Read-only scheduler snapshot. Explicit fields keep device credentials out of API responses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FirmwareUpgradeStatus(
        @JsonProperty("active_upgrades") Map<String, ActiveUpgrade> activeUpgrades,
        @JsonProperty("upgrade_queue") List<String> upgradeQueue) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActiveUpgrade(String manufacturer, String model,
            @JsonProperty("target_firmware_version") String targetFirmwareVersion) {}
}
