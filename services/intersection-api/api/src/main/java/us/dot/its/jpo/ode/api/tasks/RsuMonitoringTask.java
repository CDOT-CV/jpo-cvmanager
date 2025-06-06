package us.dot.its.jpo.ode.api.tasks;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
public class RsuMonitoringTask {

    private static final Logger log = LoggerFactory.getLogger(RsuMonitoringTask.class);

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
            try {
                String username = cred.getUsername();
                String password = cred.getPassword();
                String encPass = cred.getEncrypt_password();
                String ip = cred.getIpv4_address();
                String intersectionId = cred.getIntersection_id();

                log.info("Pulling SNMP Status for RSU: " + ip + " IntersectionID" + intersectionId);

                if (username == null || password == null || ip == null) {
                    log.warn("Cannot pull data from RSU unit. Missing Username, Password, or IP address. RSU ID: " + ip
                            + " Intersection ID: " + intersectionId);
                    continue;
                }

                // enc password is not defined for all RSU units. Try using normal password
                // instead
                if (encPass == null) {
                    encPass = password;
                }

                int uptime = snmpService.getSnmpV3Value(ip, username, password, encPass,
                        OIDMap.oids.get("rsuTimeSincePowerOn").getOid()).toInt();

                int temp = snmpService.getSnmpV3Value(ip, username, password, encPass,
                        OIDMap.oids.get("rsuIntTemp").getOid()).toInt();

                RsuState state = new RsuState();
                state.timestamp = Instant.now().toEpochMilli();
                state.rsuIP = ip;
                state.intersectionID = intersectionId;
                state.uptime = uptime;
                state.temperature = temp;

                RsuIntersectionKey key = new RsuIntersectionKey();
                key.setIntersectionId(Integer.parseInt(intersectionId));
                key.setRsuId(ip);
                key.setRegion(-1);

                kafkaService.sendRsuStatus(properties.getRsuStatusKafkaTopic(), key, state);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}