package us.dot.its.jpo.ode.api.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic OpenMetrics for {@code kafka_produced_rsu_messages_total} that
 * mimics GKE Autopilot ODE restarts: sequential instance lifetimes plus one
 * overlapping scale-out instance. Ground truth is the sum of per-scrape
 * increments across every lifetime.
 */
final class OdeRestartMetricsFixture {

    static final String RSU_IP = "10.0.0.11";
    static final String OUTPUT_TOPIC = "topic.OdeBsmJson";
    static final String INPUT_TOPIC = "topic.OdeRawEncodedBSMJson";
    static final int STEP_SECONDS = 15;
    static final int INCREMENT = 10;
    static final int BASELINE_STEP_SECONDS = 120;

    private static final List<String> TOPICS = List.of(INPUT_TOPIC, OUTPUT_TOPIC);

    /** offsetSeconds, durationSeconds, instanceId */
    private static final int[][] LIFETIMES = {
            { 0, 3600, 1 },
            { 3600, 3600, 2 },
            { 7200, 3600, 3 },
            { 3600, 1800, 4 },
    };

    private static final int WINDOW_SECONDS = 10800;

    final long startEpochSeconds;
    final long endEpochSeconds;
    final Map<String, Long> actualByTopic;
    final String openMetrics;

    private OdeRestartMetricsFixture(long startEpochSeconds, long endEpochSeconds, Map<String, Long> actualByTopic,
            String openMetrics) {
        this.startEpochSeconds = startEpochSeconds;
        this.endEpochSeconds = endEpochSeconds;
        this.actualByTopic = actualByTopic;
        this.openMetrics = openMetrics;
    }

    static OdeRestartMetricsFixture generate() {
        long end = (System.currentTimeMillis() / 1000L) - 6L * 3600L;
        long start = end - WINDOW_SECONDS;
        return generate(start, end);
    }

    static OdeRestartMetricsFixture generate(long startEpochSeconds, long endEpochSeconds) {
        Map<String, Long> actualByTopic = new LinkedHashMap<>();
        for (String topic : TOPICS) {
            actualByTopic.put(topic, 0L);
        }

        StringBuilder om = new StringBuilder();
        om.append("# HELP kafka_produced_rsu_messages_total Total RSU messages produced to Kafka by topic\n");
        om.append("# TYPE kafka_produced_rsu_messages_total counter\n");

        for (String topic : TOPICS) {
            for (int[] lifetime : LIFETIMES) {
                int offset = lifetime[0];
                int duration = lifetime[1];
                int instanceId = lifetime[2];
                long seriesStart = startEpochSeconds + offset;
                long seriesEnd = seriesStart + duration;
                String host = "ode-scraper-" + instanceId;
                long counter = 0;
                boolean first = true;
                for (long t = seriesStart; t < seriesEnd; t += STEP_SECONDS) {
                    if (first) {
                        first = false;
                    } else {
                        counter += INCREMENT;
                        actualByTopic.merge(topic, (long) INCREMENT, Long::sum);
                    }
                    om.append("kafka_produced_rsu_messages_total{environment=\"test\",job=\"ode-scraper\",")
                            .append("instance=\"").append(host).append(":8080\",host=\"").append(host)
                            .append("\",app=\"jpoode-ode\",enabled=\"true\",rsu_ip=\"").append(RSU_IP)
                            .append("\",topic=\"").append(topic).append("\"} ")
                            .append(counter).append(' ').append(t).append('\n');
                }
            }
        }
        om.append("# EOF\n");

        return new OdeRestartMetricsFixture(startEpochSeconds, endEpochSeconds, Map.copyOf(actualByTopic),
                om.toString());
    }

    long startMillis() {
        return startEpochSeconds * 1000L;
    }

    long endMillis() {
        return endEpochSeconds * 1000L;
    }

    long rangeSeconds() {
        return endEpochSeconds - startEpochSeconds;
    }
}
