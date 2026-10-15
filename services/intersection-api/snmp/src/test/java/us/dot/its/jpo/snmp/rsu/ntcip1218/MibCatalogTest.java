package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MibCatalogTest {
    @Test void numericCatalogMatchesRepositoryMibAssignments() throws Exception {
        String mib = Files.readString(Path.of("../../resources/mibs/NTCIP1218-v01.txt"));
        mib = mib.replaceAll("(?m)--[^\\r\\n]*", "");
        // Object assignments, not the MIB's human-readable DESCRIPTION OID examples.
        var pattern = Pattern.compile("(?ms)^([A-Za-z][\\w-]*)[ \\t]+(?:OBJECT IDENTIFIER|OBJECT-TYPE|MODULE-IDENTITY)\\b.*?::=\\s*\\{\\s*([\\w-]+)\\s+(\\d+)\\s*}");
        var matcher = pattern.matcher(mib);
        Map<String, String[]> nodes = new HashMap<>();
        while (matcher.find()) nodes.put(matcher.group(1), new String[]{matcher.group(2), matcher.group(3)});
        assertEquals(ForwardingTable.RECEIVE.root().value(), resolve("rsuReceivedMsgTable", nodes));
        assertEquals(ForwardingTable.TRANSMIT.root().value(), resolve("rsuXmitMsgFwdingTable", nodes));
        assertEquals("1.3.6.1.4.1.1206.4.2.18.16.10", resolve("rsuStatus", nodes));
        Map<String, String> scalars = Map.of("rsuGnssLat", "6.6", "rsuGnssLon", "6.7", "rsuFirmwareVersion", "13.2",
                "rsuSecAppCertExpiration", "8.10.1.4", "rsuSecAppCertReq", "8.10.1.5");
        scalars.forEach((name, suffix) -> assertEquals("1.3.6.1.4.1.1206.4.2.18." + suffix, resolve(name, nodes)));
        for (ForwardingTable table : ForwardingTable.values()) {
            String stem = table == ForwardingTable.RECEIVE ? "rsuReceivedMsg" : "rsuXmitMsgFwding";
            var names = new java.util.ArrayList<>(java.util.List.of("Psid", "DestIpAddr", "DestPort", "Protocol"));
            names.addAll(table == ForwardingTable.RECEIVE ? java.util.List.of("Rssi", "Interval", "DeliveryStart", "DeliveryStop", "Status", "Secure", "AuthMsgInterval")
                    : java.util.List.of("DeliveryStart", "DeliveryStop", "Secure", "Status"));
            for (int i = 0; i < names.size(); i++) assertEquals(table.root().value() + ".1." + (i + 2), resolve(stem + names.get(i), nodes));
        }
    }
    private String resolve(String name, Map<String, String[]> nodes) {
        if (name.equals("enterprises")) return "1.3.6.1.4.1";
        var node = nodes.get(name); assertNotNull(node, name);
        return resolve(node[0], nodes) + "." + node[1];
    }
}
