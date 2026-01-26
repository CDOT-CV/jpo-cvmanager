package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.net.InetAddress;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "snmp_msgfwd_config", schema = "public")
public class SnmpMsgfwdConfig {
  @SequenceGenerator(name = "snmp_msgfwd_config_id_gen", sequenceName = "snmp_msgfwd_type_id_seq", allocationSize = 1)
  @EmbeddedId
  private Integer id;

  @MapsId("rsuId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rsu_id", nullable = false)
  private Rsu rsu;

  @MapsId("msgfwdType")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "msgfwd_type", nullable = false)
  private SnmpMsgfwdType msgfwdType;

  @Size(max = 128)
  @NotNull
  @Column(name = "message_type", nullable = false, length = 128)
  private String messageType;

  @NotNull
  @Column(name = "dest_ipv4", nullable = false)
  private InetAddress destIpv4;

  @NotNull
  @Column(name = "dest_port", nullable = false)
  private Integer destPort;

  @NotNull
  @Column(name = "start_datetime", nullable = false)
  private LocalDateTime startDatetime;

  @NotNull
  @Column(name = "end_datetime", nullable = false)
  private LocalDateTime endDatetime;

  @Column(name = "active", nullable = false, columnDefinition = "bit not null")
  private boolean active;

  @Column(name = "security", nullable = false, columnDefinition = "bit not null")
  private boolean security;

}