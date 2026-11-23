package us.dot.its.jpo.ode.api.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hibernate.validator.constraints.time.DurationMax;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Resource limits for SSH reboot commands. */
@Data
@Validated
@ConfigurationProperties("rsu-reboot")
public class RsuRebootProperties {
    @Min(1)
    @Max(32)
    private int concurrency = 4;

    @Min(1)
    @Max(65535)
    private int port = 22;

    /** Total SSH attempt time; does not include waiting for the RSU to restart. */
    @NotNull
    @DurationMin(seconds = 1)
    @DurationMax(seconds = 120)
    private Duration timeout = Duration.ofSeconds(60);
}
