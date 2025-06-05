package us.dot.its.jpo.ode.api.tasks;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import us.dot.its.jpo.geojsonconverter.partitioner.RsuIntersectionKey;
import us.dot.its.jpo.ode.api.ConflictMonitorApiProperties;
import us.dot.its.jpo.ode.api.models.snmp.OIDMap;
import us.dot.its.jpo.ode.api.models.snmp.RsuState;
import us.dot.its.jpo.ode.api.services.KafkaProducerService;
import us.dot.its.jpo.ode.api.services.SNMPService;

@Component
@ConditionalOnProperty(name = "enable.monitoring", havingValue = "true", matchIfMissing = false)
public class RsuMonitoringTask {

    private static final Logger log = LoggerFactory.getLogger(RsuMonitoringTask.class);

    private SNMPService snmpService;
    private KafkaProducerService kafkaService;
    private ConflictMonitorApiProperties properties;

    @Autowired
    public RsuMonitoringTask(SNMPService snmpService, KafkaProducerService kafkaService,
            ConflictMonitorApiProperties properties) {
        this.snmpService = snmpService;
        this.kafkaService = kafkaService;
        this.properties = properties;
    }

    @Scheduled(fixedRate = 1000)
    public void queryRSUStats() {
        try {
            String username = "user";
            String password = "1234";
            String ip = "1.2.3.4";
            int uptime = snmpService.getSnmpV3Value(ip, username, password, password,
                    OIDMap.oids.get("rsuTimeSincePowerOn").getOid()).toInt();

            int temp = snmpService.getSnmpV3Value(ip, username, password, password,
                    OIDMap.oids.get("rsuIntTemp").getOid()).toInt();

            RsuState state = new RsuState();
            state.timestamp = Instant.now().toEpochMilli();
            state.rsuIP = ip;
            state.intersectionID = 1234;
            state.uptime = uptime;
            state.temperature = temp;

            RsuIntersectionKey key = new RsuIntersectionKey();
            key.setIntersectionId(1234);
            key.setRsuId(ip);
            key.setRegion(-1);

            kafkaService.sendRsuStatus(properties.getRsuStatusKafkaTopic(), key, state);

            log.info("Uptime: " + uptime + " Temperature: " + temp);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}