package us.dot.its.jpo.ode.api.models.geo;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Processed message types supported by the CV Manager polygon query. */
public enum GeoMessageType {
    BSM,
    PSM;

    /**
     * Case-insensitive lookup matching the legacy API. Unknown values map to null
     * so the service can reject them with a descriptive 400 instead of a generic
     * JSON parse error.
     */
    @JsonCreator
    public static GeoMessageType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        for (GeoMessageType type : values()) {
            if (type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }
}
