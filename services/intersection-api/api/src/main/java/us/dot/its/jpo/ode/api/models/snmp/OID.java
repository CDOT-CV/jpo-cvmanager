package us.dot.its.jpo.ode.api.models.snmp;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Setter
@EqualsAndHashCode
@Getter
public class OID {

    private String name;
    private OID_TYPE type;
    private String oid;

    public OID(String name, OID_TYPE type, String oid) {
        this.name = name;
        this.type = type;
        this.oid = oid;
    }
}

enum OID_TYPE {
    SCALAR,
    NODE,
    TABLE,
    ROW
}
