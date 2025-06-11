package us.dot.its.jpo.ode.api.tasks;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.snmp4j.smi.Variable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.geojsonconverter.partitioner.RsuIntersectionKey;
import us.dot.its.jpo.ode.api.ConflictMonitorApiProperties;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuCredentials;
import us.dot.its.jpo.ode.api.models.snmp.OIDMap;
import us.dot.its.jpo.ode.api.models.snmp.RsuState;
import us.dot.its.jpo.ode.api.services.KafkaProducerService;
import us.dot.its.jpo.ode.api.services.PostgresService;
import us.dot.its.jpo.ode.api.services.SNMPService;

@Component
@ConditionalOnProperty(name = "enable.monitoring", havingValue = "true", matchIfMissing = false)
@Slf4j
public class RsuMonitoringTask {

    private SNMPService snmpService;
    private KafkaProducerService kafkaService;
    private PostgresService postgresService;
    private ConflictMonitorApiProperties properties;

    @Autowired
    public RsuMonitoringTask(
            SNMPService snmpService,
            KafkaProducerService kafkaService,
            PostgresService postgresService,
            ConflictMonitorApiProperties properties) {
        this.snmpService = snmpService;
        this.kafkaService = kafkaService;
        this.postgresService = postgresService;
        this.properties = properties;
    }

    @Scheduled(fixedRate = 300000) // Check RSU units every 5 minutes
    public void queryRSUStats() {

        List<RsuCredentials> credentials = postgresService.getRsusWithCredentials();

        for (RsuCredentials cred : credentials) {

            String username = cred.getUsername();
            String password = cred.getPassword();
            String encPass = cred.getEncrypt_password();
            String ip = cred.getIpv4_address();
            String intersectionId = cred.getIntersection_id();

            log.info("Pulling SNMP Status for RSU: " + ip + " IntersectionID: " + intersectionId);

            if (username == null || password == null || ip == null) {
                log.warn("Cannot pull data from RSU unit. Missing Username, Password, or IP address. RSU ID: " + ip
                        + " Intersection ID: " + intersectionId);
                continue;
            }

            // enc password is not defined for all RSU units. Try using normal password
            if (encPass == null) {
                encPass = password;
            }

            RsuState state = new RsuState();
            state.timestamp = Instant.now().toEpochMilli();
            state.rsuIP = ip;
            state.intersectionID = intersectionId;

            state.uptime = getIntOID(ip, username, password, encPass, OIDMap.oids.get("rsuModeStatus").getOid());
            state.temperature = getIntOID(ip, username, password, encPass, OIDMap.oids.get("rsuIntTemp").getOid());
            state.mode = getIntOID(ip, username, password, encPass, OIDMap.oids.get("rsuModeStatus").getOid());

            RsuIntersectionKey key = new RsuIntersectionKey();
            key.setIntersectionId(Integer.parseInt(intersectionId));
            key.setRsuId(ip);
            key.setRegion(-1);

            kafkaService.sendRsuStatus(properties.getRsuStatusKafkaTopic(), key, state);
        }
    }

    public int getIntOID(String ip, String username, String password, String encPass, String oid) {
        try {
            Variable var = snmpService.getSnmpV3Value(ip, username, encPass, ip,
                    OIDMap.oids.get("rsuModeStatus").getOid());

            if (var != null) {
                return var.toInt();
            } else {
                log.warn("Query of OID " + oid + " for Intersection" + ip + " returned no value");
            }
        } catch (IOException e) {
            log.warn("Unable to Retrieve value for OID: " + oid + " for Intersection" + ip);
        }
        return -1;
    }
}
