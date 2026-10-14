package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "firmware_upgrade_rules", uniqueConstraints = {
        @UniqueConstraint(name = "firmware_upgrade_rules_source_unique", columnNames = "from_id"),
        @UniqueConstraint(name = "firmware_upgrade_rules_from_to_unique", columnNames = {"from_id", "to_id"})})
public class FirmwareUpgradeRule {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "firmware_upgrade_rules_id_gen")
    @SequenceGenerator(name = "firmware_upgrade_rules_id_gen", sequenceName = "firmware_upgrade_rules_firmware_upgrade_rule_id_seq", allocationSize = 1)
    @Column(name = "firmware_upgrade_rule_id", nullable = false)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_id", nullable = false)
    private FirmwareImage from;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_id", nullable = false)
    private FirmwareImage to;


}
