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
@Table(name = "snmp_msgfwd_type", schema = "public")
public class SnmpMsgfwdType {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "snmp_msgfwd_type_id_gen")
  @SequenceGenerator(name = "snmp_msgfwd_type_id_gen", sequenceName = "snmp_msgfwd_type_id_seq", allocationSize = 1)
  @Column(name = "snmp_msgfwd_type_id", nullable = false)
  private Integer id;

  @Size(max = 128)
  @NotNull
  @Column(name = "name", nullable = false, length = 128)
  private String name;

  @OneToMany(mappedBy = "msgfwdType")
  private Set<SnmpMsgfwdConfig> snmpMsgfwdConfigs = new LinkedHashSet<>();

}