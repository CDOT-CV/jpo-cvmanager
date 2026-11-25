package us.dot.its.jpo.ode.api.config;

import java.time.Duration;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import us.dot.its.jpo.snmp.client.SnmpClient;
import us.dot.its.jpo.snmp.execution.SnmpExecutorOptions;
import us.dot.its.jpo.snmp.execution.SnmpTaskExecutor;
import us.dot.its.jpo.snmp.snmp4j.Snmp4jClient;
import us.dot.its.jpo.snmp.snmp4j.Snmp4jOptions;
import us.dot.its.jpo.snmp.rsu.ntcip1218.Ntcip1218ForwardingTasks;

/**
 * Owns the API's shared SNMP transport and executor. Spring closes the
 * dependent executor before the transport at shutdown, allowing cancellation to
 * finish before sockets and sessions close. These beans execute requested
 * commands. They do not schedule polling.
 */
@Configuration
@ConditionalOnProperty(name = "enable.api", havingValue = "true")
@EnableConfigurationProperties(SnmpProperties.class)
public class SnmpConfig {
    @Bean(destroyMethod = "close")
    public SnmpClient snmpClient(SnmpProperties properties) {
        // Reject invalid request policies during startup, before accepting commands.
        properties.taskOptions();
        var defaults = Snmp4jOptions.defaults();
        int concurrency = properties.getMaxConcurrentTasks();
        // A batch may contain distinct credentials for every RSU. Session capacity must
        // support the configured task concurrency without requiring devices to share a
        // credential profile.
        return new Snmp4jClient(new Snmp4jOptions(defaults.requestTimeout(), defaults.readRetries(),
                defaults.operationDeadline(), defaults.admissionTimeout(), concurrency,
                Math.max(defaults.maxSessions(), concurrency), defaults.maxTargetsPerSession(),
                defaults.maxWalkBindings(), defaults.maxWalkBytes(), properties.getMaxBulkRepetitions()));
    }

    @Bean
    public Ntcip1218ForwardingTasks ntcip1218ForwardingTasks(Clock clock) {
        return new Ntcip1218ForwardingTasks(clock);
    }

    @Bean(destroyMethod = "close")
    public SnmpTaskExecutor snmpTaskExecutor(SnmpClient snmpClient, SnmpProperties properties) {
        return new SnmpTaskExecutor(snmpClient, new SnmpExecutorOptions(properties.getMaxConcurrentTasks(),
                properties.getMaxQueuedTasks(), Duration.ofSeconds(5)));
    }
}
