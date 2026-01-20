package us.dot.its.jpo.ode.api.models.postgres.tables;

import java.util.UUID;

import org.hibernate.annotations.Formula;
import org.locationtech.jts.geom.Geometry;

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
@Table(name = "rsus")
public class Rsus {

    @Id
    @Column(name = "rsu_id")
    private UUID rsuId;
    @Column(name = "geography")
    private Geometry geography;
    @Column(name = "milepost")
    private float milepost;

    @Column(name = "ipv4_address", insertable = false, updatable = false)
    private String ipv4Address;

    // Computed property for JPQL queries - extracts IP without netmask using host()
    // function
    @Formula("host(ipv4_address)")
    private String ipv4AddressText;

    @Column(name = "serial_number")
    private String serialNumber;
    @Column(name = "iss_scms_id")
    private String issScmsId;
    @Column(name = "primary_route")
    private String primaryRoute;
    @Column(name = "model")
    private int model;
    @Column(name = "credential_id")
    private int credentialId;
    @Column(name = "snmp_credential_id")
    private int snmpCredentialId;
    @Column(name = "snmp_protocol_id")
    private int snmpProtocolId;
    @Column(name = "firmware_version")
    private int firmwareVersion;
    @Column(name = "target_firmware_version")
    private int targetFirmwareVersion;
}