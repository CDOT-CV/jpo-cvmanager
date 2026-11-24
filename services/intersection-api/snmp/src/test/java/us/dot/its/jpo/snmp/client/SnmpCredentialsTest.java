package us.dot.its.jpo.snmp.client;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnmpCredentialsTest {
    @Test void equalityUsesEffectiveSecretsAndBothAlgorithms() {
        var credentials = new SnmpCredentials("fixture-user", "fixture-auth-pass", null);
        var equivalent = new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-auth-pass");
        assertEquals(credentials, equivalent);
        assertEquals(equivalent, credentials);
        assertEquals(credentials.hashCode(), equivalent.hashCode());
        assertEquals(1, new HashSet<>(List.of(credentials, equivalent)).size());

        for (var different : List.of(
                new SnmpCredentials("other-user", "fixture-auth-pass", null),
                new SnmpCredentials("fixture-user", "other-auth-pass", "fixture-auth-pass"),
                new SnmpCredentials("fixture-user", "fixture-auth-pass", "other-privacy-pass"),
                new SnmpCredentials("fixture-user", "fixture-auth-pass", null, SnmpCredentials.Authentication.SHA_256, SnmpCredentials.Privacy.AES),
                new SnmpCredentials("fixture-user", "fixture-auth-pass", null, SnmpCredentials.Authentication.SHA, SnmpCredentials.Privacy.DES))) {
            assertNotEquals(credentials, different);
        }
        assertNotEquals(null, credentials);
        assertNotEquals("fixture-user", credentials);
        assertEquals("SnmpCredentials[REDACTED]", credentials.toString());
    }
}
