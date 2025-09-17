package us.dot.its.jpo.ode.api.models.postgres.tables;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "manufacturers")
@Data
@ToString
@Setter
@EqualsAndHashCode
@Getter
public class Manufacturers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("manufacturer_id")
    private Integer manufacturerId;

    @JsonProperty("name")
    private String name;

    // Default constructor
    public Manufacturers() {
    }

    // Custom constructor
    public Manufacturers(String name) {
        this.name = name;
    }
}
