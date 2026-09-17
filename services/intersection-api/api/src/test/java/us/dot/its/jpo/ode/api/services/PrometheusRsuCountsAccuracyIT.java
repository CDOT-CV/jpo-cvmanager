package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.ode.api.models.PrometheusResponse;
import us.dot.its.jpo.ode.api.models.PrometheusResponse.PrometheusResult;

/**
 * Loads multi-instance ODE restart samples into Prometheus and compares:
 * <ul>
 * <li>actual — sum of per-scrape increments across all instance lifetimes</li>
 * <li>baseline — frozen {@code sum_over_time(increase(...)[range:step])} query</li>
 * <li>new — production {@code sum by (topic) (increase(metric[range]))} query</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
class PrometheusRsuCountsAccuracyIT {

    private static final Logger log = LoggerFactory.getLogger(PrometheusRsuCountsAccuracyIT.class);
    private static final DockerImageName PROMETHEUS_IMAGE = DockerImageName.parse("prom/prometheus:v3.1.0");

    private static OdeRestartMetricsFixture fixture;
    private static GenericContainer<?> prometheus;
    private static PrometheusService prometheusService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void startPrometheusWithRestartSamples() throws Exception {
        fixture = OdeRestartMetricsFixture.generate();
        Path omFile = Files.createTempFile("cvmanager-rsu-counts", ".om");
        Files.writeString(omFile, fixture.openMetrics);

        String startScript = "promtool tsdb create-blocks-from openmetrics /tmp/metrics.om /prometheus"
                + " && exec /bin/prometheus --config.file=/etc/prometheus/prometheus.yml"
                + " --storage.tsdb.path=/prometheus --storage.tsdb.retention.time=30d"
                + " --web.listen-address=:9090";
        prometheus = new GenericContainer<>(PROMETHEUS_IMAGE)
                .withExposedPorts(9090)
                .withCopyFileToContainer(MountableFile.forHostPath(omFile), "/tmp/metrics.om")
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withEntrypoint("/bin/sh", "-c", startScript);
                    cmd.withCmd();
                })
                .waitingFor(Wait.forHttp("/-/ready").forPort(9090).forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        prometheus.start();

        String prometheusUrl = "http://" + prometheus.getHost() + ":" + prometheus.getMappedPort(9090);
        prometheusService = new PrometheusService(new RestTemplate());
        Field urlField = PrometheusService.class.getDeclaredField("prometheusUrl");
        urlField.setAccessible(true);
        urlField.set(prometheusService, prometheusUrl);
        Field lookbehindField = PrometheusService.class.getDeclaredField("minLookbehindSeconds");
        lookbehindField.setAccessible(true);
        lookbehindField.set(prometheusService, OdeRestartMetricsFixture.BASELINE_STEP_SECONDS);
    }

    @AfterAll
    static void stopPrometheus() {
        if (prometheus != null) {
            prometheus.stop();
        }
    }

    @Test
    void newIncreaseQueryMatchesActualAndDoesNotRegressVsBaseline() throws Exception {
        String topic = OdeRestartMetricsFixture.OUTPUT_TOPIC;
        long actual = fixture.actualByTopic.get(topic);

        String baselinePromQL = PrometheusService.buildBaselineSumOverTimeIncreaseQuery(
                String.format("rsu_ip=\"%s\", topic=\"%s\"", OdeRestartMetricsFixture.RSU_IP, topic),
                "topic",
                fixture.rangeSeconds(),
                OdeRestartMetricsFixture.BASELINE_STEP_SECONDS);
        double baseline = queryInstantValue(baselinePromQL, topic);

        String newResponse = prometheusService.getRsuMessageCounts(
                OdeRestartMetricsFixture.RSU_IP, fixture.startMillis(), fixture.endMillis());
        double newValue = instantValueForTopic(newResponse, topic);

        log.info(
                "RSU counts accuracy (topic={}): actual={} baseline={} new={} |baseline-actual|={} |new-actual|={}",
                topic, actual, baseline, newValue, Math.abs(baseline - actual), Math.abs(newValue - actual));
        System.out.printf(
                "RSU counts accuracy topic=%s actual=%.0f baseline=%.4f new=%.4f%n",
                topic, (double) actual, baseline, newValue);

        // Prometheus increase() extrapolates ~1 scrape interval at series edges, so the
        // long-range query can sit slightly farther from raw increments than the 120s
        // subquery. Both must stay within 2%. VictoriaMetrics (production) does not
        // extrapolate, so the new query should track actual even more closely there.
        assertThat(baseline)
                .as("baseline subquery should match ground-truth increments across ODE instances")
                .isCloseTo((double) actual, withinPercentage(2.0));
        assertThat(newValue)
                .as("new increase() query should match ground-truth increments across ODE instances")
                .isCloseTo((double) actual, withinPercentage(2.0));
    }

    private static double queryInstantValue(String promQL, String topic) throws Exception {
        String response = prometheusService.queryInstant(promQL, fixture.endMillis());
        return instantValueForTopic(response, topic);
    }

    private static double instantValueForTopic(String response, String topic) throws Exception {
        PrometheusResponse parsed = objectMapper.readValue(response, PrometheusResponse.class);
        assertThat(parsed.isSuccess()).as("Prometheus query failed: %s", parsed.getError()).isTrue();
        for (PrometheusResult result : parsed.getResults()) {
            if (topic.equals(result.getMetricLabel("topic"))) {
                return result.getInstantValue();
            }
        }
        throw new AssertionError("No Prometheus series for topic " + topic + " in " + response);
    }
}
