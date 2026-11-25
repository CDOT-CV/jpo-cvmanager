package us.dot.its.jpo.ode.api.models.devices.management;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RsuFirmwareOption(@JsonProperty("firmware_id") Integer firmwareId, String version, boolean legacy) {}
