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

@Getter
@Setter
@Entity
@Table(name = "rsu_intersection", schema = "public")
public class RsuIntersection {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rsu_intersection_id_gen")
  @SequenceGenerator(name = "rsu_intersection_id_gen", sequenceName = "rsu_intersection_rsu_intersection_id_seq", allocationSize = 1)
  @Column(name = "rsu_intersection_id", nullable = false)
  private Integer id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rsu_id", nullable = false)
  private Rsu rsu;

}