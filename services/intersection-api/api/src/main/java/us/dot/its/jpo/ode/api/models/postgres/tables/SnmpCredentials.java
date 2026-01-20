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
@Table(name = "snmp_credentials")
public class SnmpCredentials {

    @Id
    @Column(name = "snmp_credential_id")
    private int snmpCredentialId;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "encrypt_password")
    private String encryptPassword;

    @Column(name = "nickname")
    private String nickname;

}
