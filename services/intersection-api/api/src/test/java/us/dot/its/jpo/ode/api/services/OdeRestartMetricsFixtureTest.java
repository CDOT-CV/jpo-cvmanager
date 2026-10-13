package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OdeRestartMetricsFixtureTest {

    @Test
    void actualCountsSumIncrementsAcrossRestartAndOverlapLifetimes() {
        long start = 1_700_000_000L;
        long end = start + 10_800L;
        OdeRestartMetricsFixture fixture = OdeRestartMetricsFixture.generate(start, end);

        int samplesPerHour = 3600 / OdeRestartMetricsFixture.STEP_SECONDS;
        int samplesPerHalfHour = 1800 / OdeRestartMetricsFixture.STEP_SECONDS;
        long increments = (samplesPerHour - 1L) * 3 + (samplesPerHalfHour - 1L);
        long expected = increments * OdeRestartMetricsFixture.INCREMENT;

        assertThat(fixture.actualByTopic.get(OdeRestartMetricsFixture.OUTPUT_TOPIC)).isEqualTo(expected);
        assertThat(fixture.actualByTopic.get(OdeRestartMetricsFixture.INPUT_TOPIC)).isEqualTo(expected);
        assertThat(fixture.openMetrics).contains("instance=\"ode-scraper-1:8080\"");
        assertThat(fixture.openMetrics).contains("instance=\"ode-scraper-4:8080\"");
        assertThat(fixture.openMetrics).contains("host=\"ode-scraper-2\"");
        assertThat(fixture.openMetrics.split("ode-scraper-1:8080", -1).length - 1)
                .isGreaterThan(samplesPerHour);
    }
}
