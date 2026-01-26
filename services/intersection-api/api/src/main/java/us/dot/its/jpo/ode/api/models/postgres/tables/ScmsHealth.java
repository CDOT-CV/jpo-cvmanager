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

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "scms_health", schema = "public")
public class ScmsHealth {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "scms_health_id_gen")
  @SequenceGenerator(name = "scms_health_id_gen", sequenceName = "scms_health_scms_health_id_seq", allocationSize = 1)
  @Column(name = "scms_health_id", nullable = false)
  private Integer id;

  @NotNull
  @Column(name = "\"timestamp\"", nullable = false)
  private LocalDateTime timestamp;

  @Column(name = "expiration")
  private LocalDateTime expiration;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rsu_id", nullable = false)
  private Rsu rsu;

  @Column(name = "health", nullable = false, columnDefinition = "bit not null")
  private boolean health;

}