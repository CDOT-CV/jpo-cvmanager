package com.trihydro.rsuinfobridge.service;

import com.trihydro.rsuinfobridge.models.tables.Rsu;
import com.trihydro.rsuinfobridge.testutil.TestDatabaseHelper;
import com.trihydro.rsuinfobridge.testutil.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RsuService using PostGIS Testcontainer.
 * Uses production schema (CVManager_CreateTables.sql) and sample data (CVManager_SampleData.sql).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class RsuServiceTest {

    @Autowired
    private RsuService rsuService;

    @Autowired
    private TestDatabaseHelper db;

    @BeforeEach
    void setup() {
        db.clearRsuData();
    }

    @Test
    void testGetAll_returnsAllRsus() {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 1.0, "SN001", true);
        db.createRsuWithOptions("10.10.10.11", 2.0, "SN002", false);

        // Act & Assert
        assertEquals(2, rsuService.getAll(false).size());
    }

    @Test
    void testGetAll_withTimDepositEnabled_returnsOnlyEnabled() throws UnknownHostException {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 1.0, "SN001", true);
        db.createRsuWithOptions("10.10.10.11", 2.0, "SN002", false);

        // Act
        List<Rsu> result = rsuService.getAll(true);

        // Assert
        assertEquals(1, result.size());
        assertEquals(InetAddress.getByName("10.10.10.10"), result.getFirst().getIpv4Address());
    }

    @Test
    void testGetAll_withMultipleTimDepositEnabled_returnsAll() {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 1.0, "SN001", true);
        db.createRsuWithOptions("10.10.10.11", 2.0, "SN002", true);
        db.createRsuWithOptions("10.10.10.12", 3.0, "SN003", true);

        // Act & Assert
        assertEquals(3, rsuService.getAll(true).size());
    }

    @Test
    void testGetAll_returnsCorrectFields() throws UnknownHostException {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 5.5, "SN-ABC", "SCMS-XYZ", "I-70", true, false);

        // Act
        List<Rsu> result = rsuService.getAll(false);

        // Assert
        assertEquals(1, result.size());
        Rsu rsu = result.getFirst();
        assertEquals(InetAddress.getByName("10.10.10.10"), rsu.getIpv4Address());
        assertEquals(5.5, rsu.getMilepost());
        assertEquals("SN-ABC", rsu.getSerialNumber());
        assertEquals("SCMS-XYZ", rsu.getIssScmsId());
        assertEquals("I-70", rsu.getPrimaryRoute());
        assertEquals("1218", rsu.getSnmpProtocol().getProtocolCode());
        assertEquals("username", rsu.getSnmpCredential().getUsername());
        assertTrue(rsu.getRsuOption().getTimDeposit());
        assertFalse(rsu.getRsuOption().getSnmpMonitoring());
    }

    @Test
    void testGetAll_emptyDatabase_returnsEmptyList() {
        // Act & Assert
        assertTrue(rsuService.getAll(false).isEmpty());
        assertTrue(rsuService.getAll(true).isEmpty());
    }

    @Test
    void testGetAll_noneWithTimDepositEnabled_returnsEmptyList() {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 1.0, "SN001", false);
        db.createRsuWithOptions("10.10.10.11", 2.0, "SN002", false);

        // Act & Assert
        assertTrue(rsuService.getAll(true).isEmpty());
    }

    @Test
    void testGetAll_timDepositFalse_returnsAll() {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 1.0, "SN001", true);
        db.createRsuWithOptions("10.10.10.11", 2.0, "SN002", false);

        // Act & Assert
        assertEquals(2, rsuService.getAll(false).size());
    }
}
