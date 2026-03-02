package com.trihydro.rsuinfobridge.testutil;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Test utility class for embedded database operations.
 * Provides helper methods for inserting and cleaning up test data.
 */
@Component
public class TestDatabaseHelper {

    private final JdbcTemplate jdbcTemplate;

    public TestDatabaseHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Clears all data from the database tables in the correct FK dependency order.
     */
    public void clearAllTables() {
        jdbcTemplate.execute("DELETE FROM consecutive_firmware_upgrade_failures");
        jdbcTemplate.execute("DELETE FROM max_retry_limit_reached_instances");
        jdbcTemplate.execute("DELETE FROM rsu_intersection");
        jdbcTemplate.execute("DELETE FROM snmp_msgfwd_config");
        jdbcTemplate.execute("DELETE FROM scms_health");
        jdbcTemplate.execute("DELETE FROM rsu_organization");
        jdbcTemplate.execute("DELETE FROM ping");
        jdbcTemplate.execute("DELETE FROM rsu_options");
        jdbcTemplate.execute("DELETE FROM rsus");
        jdbcTemplate.execute("DELETE FROM snmp_credentials");
        jdbcTemplate.execute("DELETE FROM rsu_credentials");
        jdbcTemplate.execute("DELETE FROM snmp_protocols");
        jdbcTemplate.execute("DELETE FROM firmware_images");
        jdbcTemplate.execute("DELETE FROM rsu_models");
        jdbcTemplate.execute("DELETE FROM manufacturers");
        jdbcTemplate.execute("DELETE FROM user_organization");
        jdbcTemplate.execute("DELETE FROM intersection_organization");
        jdbcTemplate.execute("DELETE FROM organizations");
    }

    // ==================== Insert methods ====================

    public int insertOrganization(String name) {
        jdbcTemplate.update("INSERT INTO organizations (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject(
                "SELECT organization_id FROM organizations WHERE name = ?", Integer.class, name);
    }

    public int insertManufacturer(String name) {
        jdbcTemplate.update("INSERT INTO manufacturers (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject(
                "SELECT manufacturer_id FROM manufacturers WHERE name = ?", Integer.class, name);
    }

    public int insertRsuModel(String name, String supportedRadio, int manufacturerId) {
        jdbcTemplate.update(
                "INSERT INTO rsu_models (name, supported_radio, manufacturer) VALUES (?, ?, ?)",
                name, supportedRadio, manufacturerId);
        return jdbcTemplate.queryForObject(
                "SELECT rsu_model_id FROM rsu_models WHERE name = ?", Integer.class, name);
    }

    public int insertRsuCredential(String username, String password, String nickname, int orgId) {
        jdbcTemplate.update(
                "INSERT INTO rsu_credentials (username, password, nickname, owner_organization_id) VALUES (?, ?, ?, ?)",
                username, password, nickname, orgId);
        return jdbcTemplate.queryForObject(
                "SELECT credential_id FROM rsu_credentials WHERE nickname = ?", Integer.class, nickname);
    }

    public int insertSnmpCredential(String username, String password, String nickname, int orgId) {
        jdbcTemplate.update(
                "INSERT INTO snmp_credentials (username, password, nickname, owner_organization_id) VALUES (?, ?, ?, ?)",
                username, password, nickname, orgId);
        return jdbcTemplate.queryForObject(
                "SELECT snmp_credential_id FROM snmp_credentials WHERE nickname = ?", Integer.class, nickname);
    }

    public int insertSnmpProtocol(String protocolCode, String nickname) {
        jdbcTemplate.update(
                "INSERT INTO snmp_protocols (protocol_code, nickname) VALUES (?, ?)",
                protocolCode, nickname);
        return jdbcTemplate.queryForObject(
                "SELECT snmp_protocol_id FROM snmp_protocols WHERE nickname = ?", Integer.class, nickname);
    }

    /**
     * Inserts an RSU record into the database.
     * Uses PostGIS ST_MakePoint for geography column.
     */
    public int insertRsu(String ipv4Address, double milepost,
                         String serialNumber, String issScmsId, String primaryRoute,
                         int modelId, int credentialId, int snmpCredentialId, int snmpProtocolId) {
        return insertRsu(ipv4Address, 0.0, 0.0, milepost, serialNumber, issScmsId, primaryRoute,
                modelId, credentialId, snmpCredentialId, snmpProtocolId);
    }

    /**
     * Inserts an RSU record into the database with specific coordinates.
     * Uses PostGIS ST_MakePoint for geography column.
     */
    public int insertRsu(String ipv4Address, double longitude, double latitude, double milepost,
                         String serialNumber, String issScmsId, String primaryRoute,
                         int modelId, int credentialId, int snmpCredentialId, int snmpProtocolId) {
        jdbcTemplate.update(
                "INSERT INTO rsus (geography, milepost, ipv4_address, serial_number, iss_scms_id, " +
                        "primary_route, model, credential_id, snmp_credential_id, snmp_protocol_id) " +
                        "VALUES (ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?)",
                longitude, latitude, milepost, ipv4Address, serialNumber, issScmsId,
                primaryRoute, modelId, credentialId, snmpCredentialId, snmpProtocolId);
        return jdbcTemplate.queryForObject(
                "SELECT rsu_id FROM rsus WHERE ipv4_address = ?::inet", Integer.class, ipv4Address);
    }

    public void insertRsuOption(int rsuId, boolean timDeposit, boolean snmpMonitoring) {
        jdbcTemplate.update(
                "INSERT INTO rsu_options (rsu_id, tim_deposit, snmp_monitoring) VALUES (?, ?, ?)",
                rsuId, timDeposit, snmpMonitoring);
    }

    // ==================== Convenience methods ====================

    /**
     * Sets up common prerequisite rows needed for RSU tests.
     * Creates: organization, manufacturer, model, rsu credential, snmp credential, snmp protocol.
     *
     * @return TestPrerequisites containing the IDs of all created records
     */
    public TestPrerequisites insertStandardPrerequisites() {
        int orgId = insertOrganization("TestOrg");
        int mfgId = insertManufacturer("TestManufacturer");
        int modelId = insertRsuModel("TestModel", "DSRC", mfgId);
        int credentialId = insertRsuCredential("user", "pass", "cred1", orgId);
        int snmpCredentialId = insertSnmpCredential("snmpUser", "snmpPass", "snmpCred1", orgId);
        int snmpProtocolId = insertSnmpProtocol("NTCIP1218", "NTCIP1218");

        return new TestPrerequisites(orgId, modelId, credentialId, snmpCredentialId, snmpProtocolId);
    }

    /**
     * Holds IDs of prerequisite database records created for tests.
     */
    public record TestPrerequisites(
            int organizationId,
            int modelId,
            int credentialId,
            int snmpCredentialId,
            int snmpProtocolId
    ) {}
}

