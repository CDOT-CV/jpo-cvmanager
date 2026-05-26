package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "rsu_options")
public class RsuOption {
    @Id
    @Column(name = "rsu_id", nullable = false)
    private Integer id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rsu_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Rsu rsu;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "tim_deposit", nullable = false)
    private Boolean timDeposit = false;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "snmp_monitoring", nullable = false)
    private Boolean snmpMonitoring = false;

}
