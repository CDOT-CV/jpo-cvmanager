package us.dot.its.jpo.ode.api.models.emails;

/**
 * Email delivery frequencies. {@link #toQueryValue()} returns the string used by
 * {@code UserEmailNotificationRepository} frequency filters (e.g. {@code DAILY}),
 * which differ from the enum constant names for non-immediate frequencies.
 */
public enum EmailFrequency {
    IMMEDIATE("IMMEDIATE"),
    ONCE_PER_HOUR("HOURLY"),
    ONCE_PER_DAY("DAILY"),
    ONCE_PER_WEEK("WEEKLY"),
    ONCE_PER_MONTH("MONTHLY");

    private final String queryValue;

    EmailFrequency(String queryValue) {
        this.queryValue = queryValue;
    }

    /** Value matched against frequency filters in user email notification queries. */
    public String toQueryValue() {
        return queryValue;
    }
}
