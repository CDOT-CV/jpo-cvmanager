package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import us.dot.its.jpo.ode.api.models.emails.UserEmailNotificationDto;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "user_email_notification")
public class UserEmailNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_email_notification_id_gen")
    @SequenceGenerator(name = "user_email_notification_id_gen", sequenceName = "user_email_notification_user_email_notification_id_seq", allocationSize = 1)
    @Column(name = "user_email_notification_id", nullable = false)
    @EqualsAndHashCode.Include
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "email_type_id", nullable = false)
    private EmailType emailType;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "immediate", nullable = false)
    @EqualsAndHashCode.Include
    private Boolean immediate;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "hourly", nullable = false)
    @EqualsAndHashCode.Include
    private Boolean hourly;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "daily", nullable = false)
    @EqualsAndHashCode.Include
    private Boolean daily;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "weekly", nullable = false)
    @EqualsAndHashCode.Include
    private Boolean weekly;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "monthly", nullable = false)
    @EqualsAndHashCode.Include
    private Boolean monthly;

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

    public void updateFrequency(UserEmailNotificationDto dto) {
        this.immediate = dto.getImmediate();
        this.hourly = dto.getHourly();
        this.daily = dto.getDaily();
        this.weekly = dto.getWeekly();
        this.monthly = dto.getMonthly();
    }
}