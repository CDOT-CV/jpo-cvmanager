package us.dot.its.jpo.ode.api.models.postgres.tables;

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
@Table(name = "snmp_protocols")
public class SnmpProtocols {

    @Id
    @Column(name = "snmp_protocol_id")
    private int snmpProtocolId;

    @Column(name = "protocol_code")
    private String protocolCode;

    @Column(name = "nickname")
    private String nickname;

}
