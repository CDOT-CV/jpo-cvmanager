package us.dot.its.jpo.ode.api.models.postgres.tables;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "obu_ota_requests")
@Data
@ToString
@Setter
@EqualsAndHashCode
@Getter
public class ObuOtaRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("request_id")
    private Integer requestId;

    @JsonProperty("obu_sn")
    private String obuSn;

    @JsonProperty("request_datetime")
    private LocalDateTime requestDatetime;

    @JsonProperty("origin_ip")
    private String originIp;

    @JsonProperty("obu_firmware_version")
    private String obuFirmwareVersion;

    @JsonProperty("requested_firmware_version")
    private String requestedFirmwareVersion;

    @JsonProperty("error_status")
    private Boolean errorStatus;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("manufacturer")
    private Integer manufacturer;

    // Default constructor
    public ObuOtaRequest() {
    }

    // Custom constructor
    public ObuOtaRequest(String obuSn, LocalDateTime requestDatetime, String originIp,
            String obuFirmwareVersion, String requestedFirmwareVersion,
            Boolean errorStatus, String errorMessage, Integer manufacturer) {
        this.obuSn = obuSn;
        this.requestDatetime = requestDatetime;
        this.originIp = originIp;
        this.obuFirmwareVersion = obuFirmwareVersion;
        this.requestedFirmwareVersion = requestedFirmwareVersion;
        this.errorStatus = errorStatus;
        this.errorMessage = errorMessage;
        this.manufacturer = manufacturer;
    }
}
