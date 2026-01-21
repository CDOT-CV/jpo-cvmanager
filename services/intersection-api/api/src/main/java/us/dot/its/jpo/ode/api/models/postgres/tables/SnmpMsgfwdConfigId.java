package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Embeddable
public class SnmpMsgfwdConfigId implements Serializable {
  private static final long serialVersionUID = 2723040065105563778L;
  @NotNull
  @Column(name = "rsu_id", nullable = false)
  private Integer rsuId;

  @NotNull
  @Column(name = "msgfwd_type", nullable = false)
  private Integer msgfwdType;

  @NotNull
  @Column(name = "snmp_index", nullable = false)
  private Integer snmpIndex;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
    SnmpMsgfwdConfigId entity = (SnmpMsgfwdConfigId) o;
    return Objects.equals(this.rsuId, entity.rsuId) &&
      Objects.equals(this.snmpIndex, entity.snmpIndex) &&
      Objects.equals(this.msgfwdType, entity.msgfwdType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rsuId, snmpIndex, msgfwdType);
  }

}