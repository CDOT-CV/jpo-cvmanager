package us.dot.its.jpo.ode.api.models.postgres.tables;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Setter
@EqualsAndHashCode
@Getter
@Entity
@Table(name = "scms_health")
public class ScmsHealth {

    @Id
    @Column(name = "scms_health_id")
    private int scmsHealthId;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "health")
    private boolean health;

    @Column(name = "expiration")
    private LocalDateTime expiration;

    @Column(name = "rsu_id")
    private int rsuId;
}
