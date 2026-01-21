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
@Table(name = "rsu_credentials", schema = "public")
public class RsuCredential {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rsu_credentials_id_gen")
  @SequenceGenerator(name = "rsu_credentials_id_gen", sequenceName = "rsu_credentials_credential_id_seq", allocationSize = 1)
  @Column(name = "credential_id", nullable = false)
  private Integer id;

  @Size(max = 128)
  @NotNull
  @Column(name = "username", nullable = false, length = 128)
  private String username;

  @Size(max = 128)
  @NotNull
  @Column(name = "password", nullable = false, length = 128)
  private String password;

  @Size(max = 128)
  @NotNull
  @Column(name = "nickname", nullable = false, length = 128)
  private String nickname;

  @OneToMany(mappedBy = "credential")
  private Set<Rsu> rsus = new LinkedHashSet<>();

}