package com.trihydro.rsuinfobridge.testutil;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

import com.trihydro.rsuinfobridge.models.tables.Rsu;
import com.trihydro.rsuinfobridge.models.tables.RsuCredential;
import com.trihydro.rsuinfobridge.models.tables.RsuModel;
import com.trihydro.rsuinfobridge.models.tables.RsuOption;
import com.trihydro.rsuinfobridge.models.tables.SnmpCredential;
import com.trihydro.rsuinfobridge.models.tables.SnmpProtocol;
import com.trihydro.rsuinfobridge.repository.RsuCredentialRepository;
import com.trihydro.rsuinfobridge.repository.RsuIntersectionRepository;
import com.trihydro.rsuinfobridge.repository.RsuModelRepository;
import com.trihydro.rsuinfobridge.repository.RsuOptionRepository;
import com.trihydro.rsuinfobridge.repository.RsuOrganizationRepository;
import com.trihydro.rsuinfobridge.repository.RsuRepository;
import com.trihydro.rsuinfobridge.repository.SnmpCredentialRepository;
import com.trihydro.rsuinfobridge.repository.SnmpMsgfwdConfigRepository;
import com.trihydro.rsuinfobridge.repository.SnmpProtocolRepository;

/**
 * Test utility class for database operations.
 *
 * Sample data from CVManager_SampleData.sql is pre-loaded and includes:
 * - RSU 1: IP 10.0.0.180, tim_deposit=true
 * - RSU 2: IP 10.0.0.78, tim_deposit=false
 */
@Component
public class TestDatabaseHelper {

    private final RsuIntersectionRepository rsuIntersectionRepository;
    private final SnmpMsgfwdConfigRepository snmpMsgfwdConfigRepository;
    private final RsuOrganizationRepository rsuOrganizationRepository;
    private final RsuOptionRepository rsuOptionRepository;
    private final RsuRepository rsuRepository;
    private final RsuModelRepository rsuModelRepository;
    private final RsuCredentialRepository rsuCredentialRepository;
    private final SnmpCredentialRepository snmpCredentialRepository;
    private final SnmpProtocolRepository snmpProtocolRepository;

    // IDs from CVManager_SampleData.sql
    public static final int MODEL_ID = 1;
    public static final int CREDENTIAL_ID = 1;
    public static final int SNMP_CREDENTIAL_ID = 1;
    public static final int SNMP_PROTOCOL_ID = 2;  // NTCIP 1218

    // SRID 4326 for WGS84
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    public TestDatabaseHelper(RsuIntersectionRepository rsuIntersectionRepository,
                              SnmpMsgfwdConfigRepository snmpMsgfwdConfigRepository,
                              RsuOrganizationRepository rsuOrganizationRepository,
                              RsuOptionRepository rsuOptionRepository,
                              RsuRepository rsuRepository,
                              RsuModelRepository rsuModelRepository,
                              RsuCredentialRepository rsuCredentialRepository,
                              SnmpCredentialRepository snmpCredentialRepository,
                              SnmpProtocolRepository snmpProtocolRepository) {
        this.rsuIntersectionRepository = rsuIntersectionRepository;
        this.snmpMsgfwdConfigRepository = snmpMsgfwdConfigRepository;
        this.rsuOrganizationRepository = rsuOrganizationRepository;
        this.rsuOptionRepository = rsuOptionRepository;
        this.rsuRepository = rsuRepository;
        this.rsuModelRepository = rsuModelRepository;
        this.rsuCredentialRepository = rsuCredentialRepository;
        this.snmpCredentialRepository = snmpCredentialRepository;
        this.snmpProtocolRepository = snmpProtocolRepository;
    }

    /**
     * Clears RSU data while preserving prerequisites from sample data.
     */
    public void clearRsuData() {
        rsuIntersectionRepository.deleteAll();
        snmpMsgfwdConfigRepository.deleteAll();
        rsuOrganizationRepository.deleteAll();
        rsuOptionRepository.deleteAll();
        rsuRepository.deleteAll();
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
        try {
            // Fetch reference entities from sample data
            RsuModel model = rsuModelRepository.getReferenceById(MODEL_ID);
            RsuCredential credential = rsuCredentialRepository.getReferenceById(CREDENTIAL_ID);
            SnmpCredential snmpCredential = snmpCredentialRepository.getReferenceById(SNMP_CREDENTIAL_ID);
            SnmpProtocol snmpProtocol = snmpProtocolRepository.getReferenceById(SNMP_PROTOCOL_ID);

            // Create geography point at origin (0, 0)
            Point geography = GEOMETRY_FACTORY.createPoint(new Coordinate(0, 0));

            // Build and save RSU
            Rsu rsu = Rsu.builder()
                    .geography(geography)
                    .milepost(milepost)
                    .ipv4Address(InetAddress.getByName(ipv4Address))
                    .serialNumber(serialNumber)
                    .issScmsId(issScmsId)
                    .primaryRoute(primaryRoute)
                    .model(model)
                    .credential(credential)
                    .snmpCredential(snmpCredential)
                    .snmpProtocol(snmpProtocol)
                    .build();

            Rsu savedRsu = rsuRepository.save(rsu);

            // Create and save RSU options
            RsuOption rsuOption = new RsuOption();
            rsuOption.setRsu(savedRsu);
            rsuOption.setTimDeposit(timDeposit);
            rsuOption.setSnmpMonitoring(snmpMonitoring);

            RsuOption savedRsuOption = rsuOptionRepository.save(rsuOption);

            // Link the option back to the RSU for bidirectional relationship
            savedRsu.setRsuOption(savedRsuOption);
            rsuRepository.save(savedRsu);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Invalid IP address: " + ipv4Address, e);
        }
    }
}
