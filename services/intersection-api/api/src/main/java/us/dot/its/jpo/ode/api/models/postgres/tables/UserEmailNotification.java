package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Setter
@EqualsAndHashCode
@Getter
@Entity
@Table(name = "user_email_notification")
public class UserEmailNotification {

    @Id
    @Column(name = "user_email_notification_id")
    private int userEmailNotificationId;

    @Column(name = "user_id")
    private int userId;

    @Column(name = "email_type_id")
    private int emailTypeId;

    @Column(name = "immediate")
    private boolean immediate;

    @Column(name = "hourly")
    private boolean hourly;

    @Column(name = "daily")
    private boolean daily;

    @Column(name = "weekly")
    private boolean weekly;

    @Column(name = "monthly")
    private boolean monthly;

}