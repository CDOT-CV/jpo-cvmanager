package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "consecutive_firmware_upgrade_failures", schema = "public")
public class ConsecutiveFirmwareUpgradeFailure {
  @Id
  @Column(name = "rsu_id", nullable = false)
  private Integer id;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rsu_id", nullable = false)
  private Rsu rsu;

  @NotNull
  @Column(name = "consecutive_failures", nullable = false)
  private Integer consecutiveFailures;

}