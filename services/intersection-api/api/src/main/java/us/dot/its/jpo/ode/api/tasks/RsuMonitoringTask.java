package us.dot.its.jpo.ode.api.tasks;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuCredentials;
import us.dot.its.jpo.ode.api.services.PostgresService;
import us.dot.its.jpo.ode.api.services.RsuQueryService;

@Component
@ConditionalOnProperty(name = "enable.monitoring", havingValue = "true", matchIfMissing = false)
@Slf4j
public class RsuMonitoringTask {

    private PostgresService postgresService;
    private RsuQueryService rsuQueryService;

    @Autowired
    public RsuMonitoringTask(
            RsuQueryService rsuQueryService,
            PostgresService postgresService) {
        this.rsuQueryService = rsuQueryService;
        this.postgresService = postgresService;

    }

    // @Scheduled(fixedRateString = "${monitor.interval}")
    @Scheduled(fixedRate = 5000)
    public void queryRSUStats() {
        System.out.println("Running Monitor Task" + Instant.now());

        List<RsuCredentials> credentials = postgresService.getRsusWithCredentials();

        for (RsuCredentials cred : credentials) {
            rsuQueryService.getRsuInformation(cred);

        }
        System.out.println("Running Monitor Task Finished" + Instant.now());
    }

}
