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
@Table(name = "ping")
public class Ping {

    @Id
    @Column(name = "ping_id")
    private int pingId;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "result")
    private boolean result;

    @Column(name = "rsu_id")
    private int rsuId;
}
