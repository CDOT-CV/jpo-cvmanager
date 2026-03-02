package com.trihydro.rsuinfobridge.testutil;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Test utility class for database operations.
 *
 * Sample data from CVManager_SampleData.sql is pre-loaded and includes:
 * - RSU 1: IP 10.0.0.180, tim_deposit=true
 * - RSU 2: IP 10.0.0.78, tim_deposit=false
 */
@Component
public class TestDatabaseHelper {

    private final JdbcTemplate jdbcTemplate;

    // IDs from CVManager_SampleData.sql
    public static final int MODEL_ID = 1;
    public static final int CREDENTIAL_ID = 1;
    public static final int SNMP_CREDENTIAL_ID = 1;
    public static final int SNMP_PROTOCOL_ID = 2;  // NTCIP 1218

    public TestDatabaseHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Clears RSU data while preserving prerequisites from sample data.
     */
    public void clearRsuData() {
        jdbcTemplate.execute("DELETE FROM rsu_intersection");
        jdbcTemplate.execute("DELETE FROM snmp_msgfwd_config");
        jdbcTemplate.execute("DELETE FROM rsu_organization");
        jdbcTemplate.execute("DELETE FROM rsu_options");
        jdbcTemplate.execute("DELETE FROM rsus");
    }

    /**
     * Creates an RSU with options using sample data prerequisites.
     */
    public void createRsuWithOptions(String ipv4Address, double milepost, String serialNumber, boolean timDeposit) {
        createRsuWithOptions(ipv4Address, milepost, serialNumber,
                "SCMS-" + serialNumber, "Route-" + serialNumber, timDeposit, false);
    }

    /**
     * Creates an RSU with options using full control over fields.
     */
    public void createRsuWithOptions(String ipv4Address, double milepost, String serialNumber,
                                     String issScmsId, String primaryRoute,
                                     boolean timDeposit, boolean snmpMonitoring) {
        jdbcTemplate.update(
                "INSERT INTO rsus (geography, milepost, ipv4_address, serial_number, iss_scms_id, " +
                        "primary_route, model, credential_id, snmp_credential_id, snmp_protocol_id) " +
                        "VALUES (ST_SetSRID(ST_MakePoint(0, 0), 4326)::geography, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?)",
                milepost, ipv4Address, serialNumber, issScmsId, primaryRoute,
                MODEL_ID, CREDENTIAL_ID, SNMP_CREDENTIAL_ID, SNMP_PROTOCOL_ID);

        jdbcTemplate.update(
                "INSERT INTO rsu_options (rsu_id, tim_deposit, snmp_monitoring) " +
                        "SELECT rsu_id, ?, ? FROM rsus WHERE ipv4_address = ?::inet",
                timDeposit, snmpMonitoring, ipv4Address);
    }
}
