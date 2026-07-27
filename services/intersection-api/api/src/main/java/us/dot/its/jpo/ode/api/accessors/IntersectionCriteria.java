package us.dot.its.jpo.ode.api.accessors;

import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Date;

public class IntersectionCriteria extends Criteria {

    /**
     * Accumulates the field criteria added by the builder methods.
     *
     * <p>
     * As of spring-data-mongodb 5.x the protected {@code Criteria(List, String)}
     * constructor defensively copies the criteria chain, so {@code criteria.and(field)}
     * returns a detached copy and no longer mutates the receiver. The supported way to
     * accumulate multiple fields is therefore to reassign after every {@code and(...)}
     * (i.e. {@code delegate = delegate.and(field)...}). We keep that accumulated chain
     * in {@code delegate} rather than in this instance's own (empty) chain, and expose
     * it through {@link #getCriteriaObject()} and {@link #and(String)} so that callers
     * can either consume the criteria directly or keep chaining onto it.
     */
    private Criteria delegate = new Criteria();

    /**
     * Build a query criteria object based on a time window
     *
     * @param fieldName        the db field to apply criteria to
     * @param startEpochMillis the nullable start time of the window, in
     *                         milliseconds since epoch
     * @param endEpochMillis   the nullable end time of the window, in milliseconds
     *                         since epoch
     *
     * @param timestampFormat  the format to use for the timestamp (STRING, DATE,
     *                         LONG)
     * @return the criteria object to use for querying
     */
    public IntersectionCriteria withinTimeWindow(
            @Nonnull String fieldName,
            @Nullable Long startEpochMillis,
            @Nullable Long endEpochMillis,
            @Nonnull TimeStampFormat timestampFormat) {
        if (startEpochMillis != null && endEpochMillis != null) {
            delegate = delegate.and(fieldName)
                    .gte(formatDate(startEpochMillis, timestampFormat))
                    .lte(formatDate(endEpochMillis, timestampFormat));
        } else if (startEpochMillis != null) {
            delegate = delegate.and(fieldName).gte(formatDate(startEpochMillis, timestampFormat));
        } else if (endEpochMillis != null) {
            delegate = delegate.and(fieldName).lte(formatDate(endEpochMillis, timestampFormat));
        }
        return this;
    }

    /**
     * Build a query criteria object based on a time window
     *
     * @param epochMillis     the time of the window, in milliseconds since epoch
     * @param timeStampFormat the format to use for the timestamp (STRING, DATE,
     *                        LONG)
     * @return the criteria object to use for querying
     */
    private Object formatDate(Long epochMillis, TimeStampFormat timeStampFormat) {
        switch (timeStampFormat) {
            case STRING:
                return Instant.ofEpochMilli(epochMillis).toString();
            case DATE:
                return Date.from(Instant.ofEpochMilli(epochMillis));
            case LONG:
                return epochMillis;
            default:
                throw new IllegalArgumentException("Unsupported TimeStampFormat: " + timeStampFormat);
        }
    }

    /**
     * Build a query criteria object based on a time window
     *
     * @param <T>       the type of the value to compare against
     * @param fieldName the db datetime field to apply criteria to
     * @param value     the value to compare against
     * @return the criteria object to use for querying
     */
    public <T> IntersectionCriteria whereOptional(@Nonnull String fieldName,
            @Nullable T value) {
        if (value != null) {
            delegate = delegate.and(fieldName).is(value);
        }
        return this;
    }

    /**
     * Continue the criteria chain onto a new field, preserving everything already
     * accumulated by the builder methods. Returns the underlying delegate branch so
     * callers can keep chaining (e.g. {@code criteria = criteria.and("lat").gte(..)}).
     */
    @Override
    public Criteria and(String key) {
        return delegate.and(key);
    }

    /**
     * Render the accumulated criteria as a query document. Empty when no fields were
     * added (e.g. all optional values were null).
     */
    @Override
    public Document getCriteriaObject() {
        return delegate.getCriteriaObject();
    }

    public enum TimeStampFormat {
        STRING,
        DATE,
        LONG
    }
}
