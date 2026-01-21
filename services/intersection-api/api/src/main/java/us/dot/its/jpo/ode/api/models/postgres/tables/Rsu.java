package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.locationtech.jts.geom.Geometry;

import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "rsus", schema = "public")
public class Rsu {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rsus_id_gen")
  @SequenceGenerator(name = "rsus_id_gen", sequenceName = "rsus_rsu_id_seq", allocationSize = 1)
  @Column(name = "rsu_id", nullable = false)
  private Integer id;

  @NotNull
  @Column(name = "milepost", nullable = false)
  private Double milepost;

  @NotNull
  @Column(name = "ipv4_address", nullable = false)
  // Computed property for JPQL queries - extracts IP without netmask using host()
  // function
  @Formula("host(ipv4_address)")
  private InetAddress ipv4Address;

  @Size(max = 128)
  @NotNull
  @Column(name = "serial_number", nullable = false, length = 128)
  private String serialNumber;

  @Size(max = 128)
  @NotNull
  @Column(name = "iss_scms_id", nullable = false, length = 128)
  private String issScmsId;

  @Size(max = 128)
  @NotNull
  @Column(name = "primary_route", nullable = false, length = 128)
  private String primaryRoute;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "model", nullable = false)
  private RsuModel model;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "credential_id", nullable = false)
  private RsuCredential credential;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "snmp_credential_id", nullable = false)
  private SnmpCredential snmpCredential;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "snmp_protocol_id", nullable = false)
  private SnmpProtocol snmpProtocol;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "firmware_version")
  private FirmwareImage firmwareVersion;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_firmware_version")
  private FirmwareImage targetFirmwareVersion;

  @OneToOne(mappedBy = "rsu")
  private ConsecutiveFirmwareUpgradeFailure consecutiveFirmwareUpgradeFailure;

  @OneToMany
  private Set<MaxRetryLimitReachedInstance> maxRetryLimitReachedInstances = new LinkedHashSet<>();

  @OneToMany
  private Set<Ping> pings = new LinkedHashSet<>();

  @OneToMany
  private Set<RsuIntersection> rsuIntersections = new LinkedHashSet<>();

  @OneToMany
  private Set<RsuOrganization> rsuOrganizations = new LinkedHashSet<>();

  @OneToMany
  private Set<ScmsHealth> scmsHealths = new LinkedHashSet<>();
  @OneToMany
  private Set<SnmpMsgfwdConfig> snmpMsgfwdConfigs = new LinkedHashSet<>();

  @Column(name = "geography", columnDefinition = "geography not null")
  private Geometry geography;

}