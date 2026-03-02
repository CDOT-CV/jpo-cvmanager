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
    private TestDatabaseHelper dbHelper;

    @BeforeEach
    void setup() {
        dbHelper.clearAllTables();
    }

    // ==================== Happy path tests ====================

    @Test
    void testGetAll_returnsAllRsus() {
        // Arrange
        TestPrerequisites prereqs = dbHelper.insertStandardPrerequisites();
        int rsuId1 = dbHelper.insertRsu("10.10.10.10", 1.0,
                "SN001", "SCMS001", "I-70",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId1, true, false);

        int rsuId2 = dbHelper.insertRsu("10.10.10.11", 2.0,
                "SN002", "SCMS002", "I-25",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId2, false, true);

        // Act
        List<Rsu> result = rsuService.getAll(false);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void testGetAll_withTimDepositEnabledOnly_returnsOnlyTimDepositEnabled() throws UnknownHostException {
        // Arrange
        TestPrerequisites prereqs = dbHelper.insertStandardPrerequisites();
        int rsuId1 = dbHelper.insertRsu("10.10.10.10", 1.0,
                "SN001", "SCMS001", "I-70",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId1, true, false);

        int rsuId2 = dbHelper.insertRsu("10.10.10.11", 2.0,
                "SN002", "SCMS002", "I-25",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId2, false, true);

        // Act
        List<Rsu> result = rsuService.getAll(true);

        // Assert
        assertEquals(1, result.size());
        assertEquals(InetAddress.getByName("10.10.10.10"), result.getFirst().getIpv4Address());
    }

    @Test
    void testGetAll_withMultipleTimDepositEnabled_returnsAll() {
        // Arrange
        TestPrerequisites prereqs = dbHelper.insertStandardPrerequisites();
        int rsuId1 = dbHelper.insertRsu("10.10.10.10", 1.0,
                "SN001", "SCMS001", "I-70",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId1, true, false);

        int rsuId2 = dbHelper.insertRsu("10.10.10.11", 2.0,
                "SN002", "SCMS002", "I-25",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId2, true, false);

        int rsuId3 = dbHelper.insertRsu("10.10.10.12", 3.0,
                "SN003", "SCMS003", "I-76",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId3, true, true);

        // Act
        List<Rsu> result = rsuService.getAll(true);

        // Assert
        assertEquals(3, result.size());
    }

    @Test
    void testGetAll_returnsRsuWithCorrectFields() throws UnknownHostException {
        // Arrange
        TestPrerequisites prereqs = dbHelper.insertStandardPrerequisites();
        int rsuId = dbHelper.insertRsu("10.10.10.10", 5.5,
                "SN-ABC", "SCMS-XYZ", "I-70",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId, true, false);

        // Act
        List<Rsu> result = rsuService.getAll(false);

        // Assert
        assertEquals(1, result.size());
        Rsu returned = result.getFirst();
        assertEquals(InetAddress.getByName("10.10.10.10"), returned.getIpv4Address());
        assertEquals("NTCIP1218", returned.getSnmpProtocol().getProtocolCode());
        assertEquals("snmpUser", returned.getSnmpCredential().getUsername());
        assertEquals("snmpPass", returned.getSnmpCredential().getPassword());
        assertEquals(5.5, returned.getMilepost());
        assertEquals("SN-ABC", returned.getSerialNumber());
        assertEquals("SCMS-XYZ", returned.getIssScmsId());
        assertEquals("I-70", returned.getPrimaryRoute());
        assertTrue(returned.getRsuOption().getTimDeposit());
        assertFalse(returned.getRsuOption().getSnmpMonitoring());
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
        TestPrerequisites prereqs = dbHelper.insertStandardPrerequisites();
        int rsuId1 = dbHelper.insertRsu("10.10.10.10", 1.0,
                "SN001", "SCMS001", "I-70",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId1, false, false);

        int rsuId2 = dbHelper.insertRsu("10.10.10.11", 2.0,
                "SN002", "SCMS002", "I-25",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId2, false, true);

        // Act
        List<Rsu> result = rsuService.getAll(true);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAll_timDepositEnabledFalse_returnsAllIncludingDisabled() {
        // Arrange
        TestPrerequisites prereqs = dbHelper.insertStandardPrerequisites();
        int rsuId1 = dbHelper.insertRsu("10.10.10.10", 1.0,
                "SN001", "SCMS001", "I-70",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId1, true, false);

        int rsuId2 = dbHelper.insertRsu("10.10.10.11", 2.0,
                "SN002", "SCMS002", "I-25",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId2, false, false);

        // Act
        List<Rsu> result = rsuService.getAll(false);

        // Assert - should return all RSUs regardless of timDeposit setting
        assertEquals(2, result.size());
    }

    @Test
    void testGetAll_singleRsu_returnsSingleElement() throws UnknownHostException {
        // Arrange
        TestPrerequisites prereqs = dbHelper.insertStandardPrerequisites();
        int rsuId = dbHelper.insertRsu("192.168.1.1", 10.0,
                "SINGLE", "SCMS-SINGLE", "US-36",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId, false, false);

        // Act
        List<Rsu> result = rsuService.getAll(false);

        // Assert
        assertEquals(1, result.size());
        assertEquals(InetAddress.getByName("192.168.1.1"), result.getFirst().getIpv4Address());
    }

    @Test
    void testGetAll_sharedCredentials_returnsAllRsus() {
        // Arrange - multiple RSUs sharing the same credentials
        TestPrerequisites prereqs = dbHelper.insertStandardPrerequisites();
        int rsuId1 = dbHelper.insertRsu("10.0.0.1", 1.0,
                "SN001", "SCMS001", "I-70",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId1, true, false);

        int rsuId2 = dbHelper.insertRsu("10.0.0.2", 2.0,
                "SN002", "SCMS002", "I-25",
                prereqs.modelId(), prereqs.credentialId(), prereqs.snmpCredentialId(), prereqs.snmpProtocolId());
        dbHelper.insertRsuOption(rsuId2, true, false);

        // Act
        List<Rsu> resultAll = rsuService.getAll(false);
        List<Rsu> resultTimDeposit = rsuService.getAll(true);

        // Assert
        assertEquals(2, resultAll.size());
        assertEquals(2, resultTimDeposit.size());
    }
}
