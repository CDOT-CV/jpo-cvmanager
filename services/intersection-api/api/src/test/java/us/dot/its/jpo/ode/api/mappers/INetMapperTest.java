package us.dot.its.jpo.ode.api.mappers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import us.dot.its.jpo.ode.api.services.RsuManagementService;

import java.net.Inet4Address;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class INetMapperTest {

    private INetMapper iNetMapper;

    @BeforeEach
    void setUp() {
        iNetMapper = new INetMapperImpl();
    }

    @Nested
    @DisplayName("Tests for mapInet4AddressToString mapper method")
    class MapInet4AddressToStringTests {
        @Test
        void testMapInet4AddressToString_ValidIpv4() throws UnknownHostException {
            Inet4Address address = RsuManagementService.toIpv4Address("192.168.1.100");

            String result = iNetMapper.mapInet4AddressToString(address);

            assertNotNull(result);
            assertEquals("192.168.1.100", result);
        }

        @Test
        void testMapInet4AddressToString_Null() {
            String result = iNetMapper.mapInet4AddressToString(null);

            assertNull(result);
        }

        @Test
        void testMapInet4AddressToString_Localhost() throws UnknownHostException {
            Inet4Address address = RsuManagementService.toIpv4Address("127.0.0.1");

            String result = iNetMapper.mapInet4AddressToString(address);

            assertNotNull(result);
            assertEquals("127.0.0.1", result);
        }
    }

    @Nested
    @DisplayName("Tests for mapStringToInet4Address mapper method")
    class MapStringToInet4AddressTests {
        @Test
        void testMapStringToInet4Address_ValidIpv4() {
            Inet4Address result = iNetMapper.mapStringToInet4Address("192.168.1.100");

            assertNotNull(result);
            assertEquals("192.168.1.100", result.getHostAddress());
        }

        @Test
        void testMapStringToInet4Address_Null() {
            Inet4Address result = iNetMapper.mapStringToInet4Address(null);

            assertNull(result);
        }

        @Test
        void testMapStringToInet4Address_InvalidIp() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> iNetMapper.mapStringToInet4Address("invalid-ip"));

            assertTrue(exception.getMessage().contains("Invalid IP address, should be like #.#.#.#, but was "));
            assertTrue(exception.getMessage().contains("invalid-ip"));
        }
    }

    @Nested
    @DisplayName("Round-trip tests for Inet4Address <-> String mapping")
    class RoundTripTests {
        @Test
        void testRoundTrip_ValidIpv4() {
            String originalIp = "192.168.1.100";

            Inet4Address Inet4Address = iNetMapper.mapStringToInet4Address(originalIp);
            String resultIp = iNetMapper.mapInet4AddressToString(Inet4Address);

            assertNotNull(resultIp);
            assertEquals(originalIp, resultIp);
        }
    }
}