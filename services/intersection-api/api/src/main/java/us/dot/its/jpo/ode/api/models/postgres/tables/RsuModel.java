package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "rsu_models", schema = "public")
public class RsuModel {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rsu_models_id_gen")
  @SequenceGenerator(name = "rsu_models_id_gen", sequenceName = "rsu_models_rsu_model_id_seq", allocationSize = 1)
  @Column(name = "rsu_model_id", nullable = false)
  private Integer id;

  @Size(max = 128)
  @NotNull
  @Column(name = "name", nullable = false, length = 128)
  private String name;

  @Size(max = 128)
  @NotNull
  @Column(name = "supported_radio", nullable = false, length = 128)
  private String supportedRadio;

  @OneToMany(mappedBy = "model")
  private Set<FirmwareImage> firmwareImages = new LinkedHashSet<>();

  @OneToMany(mappedBy = "model")
  private Set<Rsu> rsus = new LinkedHashSet<>();

}