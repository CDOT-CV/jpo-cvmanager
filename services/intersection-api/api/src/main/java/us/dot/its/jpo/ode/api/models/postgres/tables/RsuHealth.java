package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "rsu_health")
public class RsuHealth {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rsu_health_id_gen")
    @SequenceGenerator(name = "rsu_health_id_gen", sequenceName = "rsu_health_rsu_health_id_seq", allocationSize = 1)
    @Column(name = "rsu_health_id", nullable = false)
    private Integer id;

    @NotNull
    @Column(name = "\"timestamp\"", nullable = false)
    private Instant timestamp;

    @NotNull
    @Column(name = "health", nullable = false)
    private Integer health;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rsu_id", nullable = false)
    private Rsu rsu;

}