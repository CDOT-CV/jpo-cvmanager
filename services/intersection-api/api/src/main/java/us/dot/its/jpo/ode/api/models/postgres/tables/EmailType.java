package us.dot.its.jpo.ode.api.models.postgres.tables;

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
    private int email_type_id;
    private String email_type;
    private String description;
    private int required_role;
}