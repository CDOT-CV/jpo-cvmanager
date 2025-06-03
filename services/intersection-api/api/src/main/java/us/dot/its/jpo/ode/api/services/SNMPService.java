package us.dot.its.jpo.ode.api.services;

import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.TSM;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.stereotype.Service;

import java.io.IOException;

import javax.annotation.PostConstruct;

@Service
public class SNMPService {

    public static String uptimeOID = "1.3.6.1.4.1.1206.4.2.18.12.1.0";
    public static String rsuLocationLatOID = "1.3.6.1.4.1.1206.4.2.18.13.5.0";
    public static String rsuIDOID = "1.3.6.1.4.1.1206.4.2.18.13.4.0";

    private Snmp snmp;

    @PostConstruct
    public void init() throws Exception {
        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
        snmp = new Snmp(transport);
        transport.listen();
    }

    public String getAsString(String ipAddress, String community, String oid) throws Exception {
        CommunityTarget<UdpAddress> target = new CommunityTarget<>();
        target.setCommunity(new OctetString(community));
        target.setAddress(new UdpAddress(ipAddress + "/161"));
        target.setRetries(2);
        target.setTimeout(1500);
        target.setVersion(SnmpConstants.version2c);

        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oid)));
        pdu.setType(PDU.GET);

        ResponseEvent responseEvent = snmp.send(pdu, target);
        if (responseEvent != null && responseEvent.getResponse() != null) {
            VariableBinding vb = responseEvent.getResponse().get(0);
            return vb.getVariable().toString();
        } else {
            throw new RuntimeException("SNMP GET timed out or returned null.");
        }
    }

    public void setSnmpV3Value(
            String ipAddress,
            String username,
            String authPass,
            String oid,
            int intValue // directly passing integer for clarity
    ) throws Exception {

        // Setup SNMP and transport
        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
        Snmp snmp = new Snmp(transport);
        transport.listen();

        // Add USM and user
        USM usm = new USM(
                SecurityProtocols.getInstance(),
                new OctetString(MPv3.createLocalEngineID()),
                0);
        SecurityModels.getInstance().addSecurityModel(usm);

        SecurityProtocols.getInstance().addAuthenticationProtocol(new AuthSHA());
        SecurityProtocols.getInstance().addPrivacyProtocol(new PrivAES128());

        snmp.getUSM().addUser(
                new OctetString(username),
                new UsmUser(
                        new OctetString(username),
                        AuthSHA.ID, new OctetString(authPass),
                        PrivAES128.ID, new OctetString(authPass)));

        // Configure the target
        UserTarget<UdpAddress> target = new UserTarget<>();
        target.setAddress(new UdpAddress(ipAddress + "/161"));
        target.setRetries(2);
        target.setTimeout(3000);
        target.setVersion(SnmpConstants.version3);
        target.setSecurityLevel(SecurityLevel.AUTH_PRIV);
        target.setSecurityName(new OctetString(username));

        // Create a ScopedPDU for SET
        ScopedPDU pdu = new ScopedPDU();
        pdu.setType(PDU.SET);
        pdu.add(new VariableBinding(new OID(oid), new Integer32(intValue)));

        // Send and handle response
        ResponseEvent response = snmp.send(pdu, target);

        System.out.println("Response" + response);
        System.out.println("Response" + response.getResponse());

        if (response == null || response.getResponse() == null) {

            throw new RuntimeException("SNMP SET timed out or failed");
        }

        if (response.getResponse().getErrorStatus() != PDU.noError) {
            throw new RuntimeException("SNMP SET error: " +
                    response.getResponse().getErrorStatusText());
        }

        snmp.close();
    }
}
