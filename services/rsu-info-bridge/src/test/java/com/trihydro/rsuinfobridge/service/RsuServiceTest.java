package com.trihydro.rsuinfobridge.service;

import com.trihydro.rsuinfobridge.models.tables.Rsu;
import com.trihydro.rsuinfobridge.testutil.TestDatabaseHelper;
import com.trihydro.rsuinfobridge.testutil.TestDatabaseHelper.TestPrerequisites;
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

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class RsuServiceTest {

    @Autowired
    private RsuService rsuService;

    @Autowired
    private TestDatabaseHelper db;

    private TestPrerequisites prereqs;

    @BeforeEach
    void setup() {
        db.clearAllTables();
        prereqs = db.insertStandardPrerequisites();
    }

    // ==================== Happy path tests ====================

    @Test
    void testGetAll_returnsAllRsus() {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 1.0, "SN001", true, prereqs);
        db.createRsuWithOptions("10.10.10.11", 2.0, "SN002", false, prereqs);

        // Act
        List<Rsu> result = rsuService.getAll(false);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void testGetAll_withTimDepositEnabledOnly_returnsOnlyTimDepositEnabled() throws UnknownHostException {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 1.0, "SN001", true, prereqs);
        db.createRsuWithOptions("10.10.10.11", 2.0, "SN002", false, prereqs);

        // Act
        List<Rsu> result = rsuService.getAll(true);

        // Assert
        assertEquals(1, result.size());
        assertEquals(InetAddress.getByName("10.10.10.10"), result.getFirst().getIpv4Address());
    }

    @Test
    void testGetAll_withMultipleTimDepositEnabled_returnsAll() {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 1.0, "SN001", true, prereqs);
        db.createRsuWithOptions("10.10.10.11", 2.0, "SN002", true, prereqs);
        db.createRsuWithOptions("10.10.10.12", 3.0, "SN003", true, prereqs);

        // Act
        List<Rsu> result = rsuService.getAll(true);

        // Assert
        assertEquals(3, result.size());
    }

    @Test
    void testGetAll_returnsRsuWithCorrectFields() throws UnknownHostException {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 5.5, "SN-ABC", "SCMS-XYZ", "I-70", true, false, prereqs);

        // Act
        List<Rsu> result = rsuService.getAll(false);

        // Assert
        assertEquals(1, result.size());
        Rsu rsu = result.getFirst();
        assertEquals(InetAddress.getByName("10.10.10.10"), rsu.getIpv4Address());
        assertEquals("NTCIP1218", rsu.getSnmpProtocol().getProtocolCode());
        assertEquals("snmpUser", rsu.getSnmpCredential().getUsername());
        assertEquals("snmpPass", rsu.getSnmpCredential().getPassword());
        assertEquals(5.5, rsu.getMilepost());
        assertEquals("SN-ABC", rsu.getSerialNumber());
        assertEquals("SCMS-XYZ", rsu.getIssScmsId());
        assertEquals("I-70", rsu.getPrimaryRoute());
        assertTrue(rsu.getRsuOption().getTimDeposit());
        assertFalse(rsu.getRsuOption().getSnmpMonitoring());
    }

    // ==================== Edge case tests ====================

    @Test
    void testGetAll_emptyDatabase_returnsEmptyList() {
        // Act
        List<Rsu> result = rsuService.getAll(false);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAll_withTimDepositEnabled_emptyDatabase_returnsEmptyList() {
        // Act
        List<Rsu> result = rsuService.getAll(true);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAll_withTimDepositEnabled_noneEnabled_returnsEmptyList() {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 1.0, "SN001", false, prereqs);
        db.createRsuWithOptions("10.10.10.11", 2.0, "SN002", false, prereqs);

        // Act
        List<Rsu> result = rsuService.getAll(true);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAll_timDepositFalse_returnsAllIncludingDisabled() {
        // Arrange
        db.createRsuWithOptions("10.10.10.10", 1.0, "SN001", true, prereqs);
        db.createRsuWithOptions("10.10.10.11", 2.0, "SN002", false, prereqs);

        // Act
        List<Rsu> result = rsuService.getAll(false);

        // Assert - should return all RSUs regardless of timDeposit setting
        assertEquals(2, result.size());
    }

    @Test
    void testGetAll_singleRsu_returnsSingleElement() throws UnknownHostException {
        // Arrange
        db.createRsuWithOptions("192.168.1.1", 10.0, "SINGLE", false, prereqs);

        // Act
        List<Rsu> result = rsuService.getAll(false);

        // Assert
        assertEquals(1, result.size());
        assertEquals(InetAddress.getByName("192.168.1.1"), result.getFirst().getIpv4Address());
    }

    @Test
    void testGetAll_multipleRsusWithSharedCredentials_returnsAll() {
        // Arrange - multiple RSUs sharing the same credentials
        db.createRsuWithOptions("10.0.0.1", 1.0, "SN001", true, prereqs);
        db.createRsuWithOptions("10.0.0.2", 2.0, "SN002", true, prereqs);

        // Act
        List<Rsu> resultAll = rsuService.getAll(false);
        List<Rsu> resultTimDeposit = rsuService.getAll(true);

        // Assert
        assertEquals(2, resultAll.size());
        assertEquals(2, resultTimDeposit.size());
    }
}
