package com.trihydro.rsuinfobridge.models.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RsuDto {
    @NotBlank(message = "id is required")
    private String id;

    @NotBlank(message = "ipv4Address is required")
    private String ipv4Address;

    @NotBlank(message = "snmpProtocol is required")
    private String snmpProtocol;

    @NotBlank(message = "snmpUsername is required")
    private String snmpUsername;

    @NotBlank(message = "snmpPassword is required")
    private String snmpPassword;

    private String authenticationProtocol; // optional

    private String privacyProtocol; // optional

    @NotNull(message = "latitude is required")
    private Double latitude;

    @NotNull(message = "longitude is required")
    private Double longitude;

    @NotNull(message = "timDepositEnabled is required")
    private Boolean timDepositEnabled;
}
