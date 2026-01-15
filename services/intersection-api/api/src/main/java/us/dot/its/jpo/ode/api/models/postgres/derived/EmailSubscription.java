package us.dot.its.jpo.ode.api.models.postgres.derived;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class EmailSubscription {
    private String category;
    private String description;
    private String requiredRole;
    private Boolean immediate;
    private Boolean hourly;
    private Boolean daily;
    private Boolean weekly;
    private Boolean monthly;
    private Boolean supports_immediate;
    private Boolean supports_hourly;
    private Boolean supports_daily;
    private Boolean supports_weekly;
    private Boolean supports_monthly;

    public Boolean getSubscribed() {
        return immediate != null || hourly != null || daily != null || weekly != null || monthly != null;
    }

    public Boolean isFrequencyEqual(EmailSubscription other) {
        return (this.immediate != null && other.immediate != null && this.immediate.equals(other.immediate))
                || (this.hourly != null && other.hourly != null && this.hourly.equals(other.hourly))
                || (this.daily != null && other.daily != null && this.daily.equals(other.daily))
                || (this.weekly != null && other.weekly != null && this.weekly.equals(other.weekly))
                || (this.monthly != null && other.monthly != null && this.monthly.equals(other.monthly));
    }
}