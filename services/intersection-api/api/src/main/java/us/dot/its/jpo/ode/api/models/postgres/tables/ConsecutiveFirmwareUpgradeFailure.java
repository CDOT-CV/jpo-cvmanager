package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "consecutive_firmware_upgrade_failures")
public class ConsecutiveFirmwareUpgradeFailure {
    @Id
    @Column(name = "rsu_id", nullable = false)
    private Integer id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rsu_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Rsu rsu;

    @NotNull
    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures;

}
