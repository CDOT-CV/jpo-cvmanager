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
@Table(name = "rsu_models")
public class RsuModels {

    @Id
    @Column(name = "rsu_model_id")
    private int rsuModelId;

    @Column(name = "name")
    private String name;

    @Column(name = "supported_radio")
    private String supportedRadio;

    @Column(name = "manufacturer")
    private int manufacturer;

}
