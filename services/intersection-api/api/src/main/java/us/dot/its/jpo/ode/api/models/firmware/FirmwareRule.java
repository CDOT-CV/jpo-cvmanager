package us.dot.its.jpo.ode.api.models.firmware;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Entity
@Table(name = "firmware_upgrade_rules")
@Data
public class FirmwareRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("firmware_upgrade_rule_id")
    private Integer firmwareUpgradeRuleId;

    @NotNull(message = "From firmware is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_id")
    @JsonProperty("from_firmware")
    private FirmwareFile fromFirmware;

    @NotNull(message = "To firmware is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_id")
    @JsonProperty("to_firmware")
    private FirmwareFile toFirmware;

    // Custom constructor for creating new firmware upgrade rules
    public FirmwareRule(FirmwareFile fromFirmware, FirmwareFile toFirmware) {
        this.fromFirmware = fromFirmware;
        this.toFirmware = toFirmware;
    }

    // Default constructor
    public FirmwareRule() {
    }
}
