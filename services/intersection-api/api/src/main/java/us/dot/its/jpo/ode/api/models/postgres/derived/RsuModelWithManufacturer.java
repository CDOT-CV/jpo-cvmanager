package us.dot.its.jpo.ode.api.models.postgres.derived;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for RSU model with manufacturer information
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RsuModelWithManufacturer {
    private String manufacturer;
    private String model;
}
