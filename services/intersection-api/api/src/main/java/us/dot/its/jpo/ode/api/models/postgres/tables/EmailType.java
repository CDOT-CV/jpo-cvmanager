package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Setter
@EqualsAndHashCode
@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "email_type")
public class EmailType {
    @Id
    @Column(name = "email_type_id")
    private int emailTypeId;

    @Column(name = "email_type")
    private String emailType;

    @Column(name = "description")
    private String description;

    @Column(name = "required_role")
    private int requiredRole;

    @Column(name = "supports_immediate")
    private boolean supportsImmediate;

    @Column(name = "supports_hourly")
    private boolean supportsHourly;

    @Column(name = "supports_daily")
    private boolean supportsDaily;

    @Column(name = "supports_weekly")
    private boolean supportsWeekly;

    @Column(name = "supports_monthly")
    private boolean supportsMonthly;

}