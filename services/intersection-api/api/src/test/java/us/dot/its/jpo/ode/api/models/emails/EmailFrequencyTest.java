package us.dot.its.jpo.ode.api.models.emails;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class EmailFrequencyTest {

    @Test
    void toQueryValueMatchesUserEmailNotificationRepositoryFilters() {
        assertThat(EmailFrequency.IMMEDIATE.toQueryValue()).isEqualTo("IMMEDIATE");
        assertThat(EmailFrequency.ONCE_PER_HOUR.toQueryValue()).isEqualTo("HOURLY");
        assertThat(EmailFrequency.ONCE_PER_DAY.toQueryValue()).isEqualTo("DAILY");
        assertThat(EmailFrequency.ONCE_PER_WEEK.toQueryValue()).isEqualTo("WEEKLY");
        assertThat(EmailFrequency.ONCE_PER_MONTH.toQueryValue()).isEqualTo("MONTHLY");
    }
}
