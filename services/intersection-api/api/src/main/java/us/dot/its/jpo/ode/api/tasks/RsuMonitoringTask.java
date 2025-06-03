package us.dot.its.jpo.ode.api.tasks;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.Notification;
import us.dot.its.jpo.ode.api.accessors.map.ProcessedMapRepository;
import us.dot.its.jpo.ode.api.models.EmailFrequency;
import us.dot.its.jpo.ode.api.services.ReportService;
import us.dot.its.jpo.ode.api.services.SNMPService;

@Component
@ConditionalOnProperty(name = "enable.monitoring", havingValue = "true", matchIfMissing = false)
public class RsuMonitoringTask {

    private static final Logger log = LoggerFactory.getLogger(EmailTask.class);

    @Autowired
    private SNMPService snmpService;

    @Autowired
    public RsuMonitoringTask(SNMPService snmpService) {
        this.snmpService = snmpService;
    }

    @Scheduled(fixedRate = 1000)
    public void queryRSUStats() {
        try {
            String result = snmpService.getAsString("172.250.250.93", "public", snmpService.rsuLocationLatOID);
            log.info("SNMP Result: " + result);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}