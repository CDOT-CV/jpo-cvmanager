package us.dot.its.jpo.ode.api.models.emails;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@AllArgsConstructor
@NoArgsConstructor
@Data
@With
public class UserEmailNotificationDto {
    public String category;
    public String description;
    @JsonProperty("required_role")
    private String requiredRole;
    public Boolean immediate;
    public Boolean hourly;
    public Boolean daily;
    public Boolean weekly;
    public Boolean monthly;
    @JsonProperty("supports_immediate")
    public Boolean supportsImmediate;
    @JsonProperty("supports_hourly")
    public Boolean supportsHourly;
    @JsonProperty("supports_daily")
    public Boolean supportsDaily;
    @JsonProperty("supports_weekly")
    public Boolean supportsWeekly;
    @JsonProperty("supports_monthly")
    public Boolean supportsMonthly;

    public Boolean getSubscribed() {
        return (immediate != null && immediate) ||
                (hourly != null && hourly) ||
                (daily != null && daily) ||
                (weekly != null && weekly) ||
                (monthly != null && monthly);
    }

    public Boolean isFrequencyEqual(UserEmailNotificationDto other) {
        return (this.immediate != null && other.immediate != null && this.immediate.equals(other.immediate))
                && (this.hourly != null && other.hourly != null && this.hourly.equals(other.hourly))
                && (this.daily != null && other.daily != null && this.daily.equals(other.daily))
                && (this.weekly != null && other.weekly != null && this.weekly.equals(other.weekly))
                && (this.monthly != null && other.monthly != null && this.monthly.equals(other.monthly));
    }
}