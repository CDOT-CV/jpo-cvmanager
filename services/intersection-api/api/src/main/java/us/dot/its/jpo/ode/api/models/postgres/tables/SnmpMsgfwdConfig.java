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
@Table(name = "snmp_msgfwd_config")
public class SnmpMsgfwdConfig {

    @Id
    @Column(name = "rsu_id")
    private int rsuId;

    @Column(name = "msgfwd_type")
    private int msgfwdType;

    @Column(name = "snmp_index")
    private int snmpIndex;

    @Column(name = "message_type")
    private int messageType;

    @Column(name = "dest_ipv4")
    private String destIpv4;

    @Column(name = "dest_port")
    private int destPort;

    @Column(name = "start_datetime")
    private LocalDateTime startDatetime;

    @Column(name = "end_datetime")
    private LocalDateTime endDatetime;

    @Column(name = "active")
    private boolean active;

    @Column(name = "security")
    private boolean security;
}
