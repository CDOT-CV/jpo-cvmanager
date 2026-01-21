package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "ping", schema = "public")
public class Ping {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ping_id_gen")
  @SequenceGenerator(name = "ping_id_gen", sequenceName = "ping_ping_id_seq", allocationSize = 1)
  @Column(name = "ping_id", nullable = false)
  private Integer id;

  @NotNull
  @Column(name = "timestamp", nullable = false)
  private LocalDateTime timestamp;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rsu_id", nullable = false)
  private Rsu rsu;

  @Column(name = "result", nullable = false, columnDefinition = "bit not null")
  private boolean result;

}