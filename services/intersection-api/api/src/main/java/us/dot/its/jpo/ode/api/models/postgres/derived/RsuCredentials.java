package us.dot.its.jpo.ode.api.models.postgres.derived;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Setter
@EqualsAndHashCode
@Getter
@AllArgsConstructor
public class RsuCredentials {
    private int rsu_id;
    private String ipv4_address;
    private String username;
    private String password;
    private String encrypt_password;
    private String protocol_code;
    private String intersection_id;
}
